package Hamza.GP.ChatBot;

import Hamza.GP.Appuser.AppUser;
import Hamza.GP.Appuser.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuestionAnswerService {
    private final RestTemplate restTemplate;
    private final String apiKey;
    private static final String ASSISTANT_ID = "asst_1GORjnI6QmuaZOBIqQEKUela";
    private final Map<String, Map<String, Object>> responses = new ConcurrentHashMap<>();
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final QuestionAnswerRepository questionAnswerRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QuestionAnswerService(RestTemplate restTemplate, String apiKey, UserRepository userRepository, ConversationRepository conversationRepository, QuestionAnswerRepository questionAnswerRepository) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.questionAnswerRepository = questionAnswerRepository;
    }

    public Map<String, String> getAnswer(String userQuestion, String conversationId, String email, String threadId, List<String> vectorStoreIds) throws Exception {
        AppUser user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        boolean blindMode = userRepository.findBlindModeByEmail(email);

        Conversation conversation;
        if (conversationId == null || !conversationRepository.existsById(conversationId)) {
            conversationId = conversationId != null ? conversationId : UUID.randomUUID().toString();
            conversation = new Conversation(user, userQuestion, conversationId);
        } else {
            conversation = conversationRepository.findById(conversationId).orElseThrow(() -> new RuntimeException("Conversation not found"));
        }

        Map<String, Object> response = askQuestion(userQuestion, threadId, blindMode, vectorStoreIds);

        String answerAsJson = objectMapper.writeValueAsString(response.get("answer"));
        String responseId = blindMode ? (String) response.get("response_id") : null;
        String returnedThreadId = (String) response.get("thread_id");

        if (conversation.getThreadId() == null && returnedThreadId != null) {
            conversation.setThreadId(returnedThreadId);
        }
        conversationRepository.save(conversation);

        QuestionAnswer questionAnswer = new QuestionAnswer();
        questionAnswer.setQuestion(userQuestion);
        questionAnswer.setAnswer(answerAsJson);
        questionAnswer.setConversation(conversation);
        questionAnswer.setResponse_id(responseId);
        questionAnswerRepository.save(questionAnswer);

        Map<String, String> result = new HashMap<>();
        result.put("answer", answerAsJson);
        result.put("thread_id", returnedThreadId);
        result.put("conversationId", conversation.getId());
        result.put("response_id", responseId);
        return result;
    }


    public Map<String, Object> askQuestion(String question, String threadId, boolean blindMode, List<String> vectorStoreIds) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("OpenAI-Beta", "assistants=v2");


        // Step 1: Create or reuse a thread
        if (threadId == null || threadId.isEmpty()) {
            HttpEntity<String> createThreadEntity = new HttpEntity<>("{}", headers);
            ResponseEntity<Map> createThreadResponse = restTemplate.postForEntity("https://api.openai.com/v1/threads", createThreadEntity, Map.class);
            threadId = (String) createThreadResponse.getBody().get("id");
        }

        // Step 2: Add user message to the thread
        Map<String, Object> messageBody = Map.of(
                "role", "user",
                "content", question
        );
        HttpEntity<Map<String, Object>> messageEntity = new HttpEntity<>(messageBody, headers);
        restTemplate.postForEntity("https://api.openai.com/v1/threads/" + threadId + "/messages", messageEntity, Map.class);

        // Step 3: Run the assistant
        Map<String, Object> runBody = new HashMap<>();
        runBody.put("assistant_id", ASSISTANT_ID);

        if (vectorStoreIds != null && !vectorStoreIds.isEmpty()) {
            runBody.put("tool_resources", Map.of(
                    "file_search", Map.of(
                            "vector_store_ids", vectorStoreIds
                    )
            ));
        }

        HttpEntity<Map<String, Object>> runEntity = new HttpEntity<>(runBody, headers);
        ResponseEntity<Map> runResponse = restTemplate.postForEntity("https://api.openai.com/v1/threads/" + threadId + "/runs", runEntity, Map.class);
        String runId = (String) runResponse.getBody().get("id");

        // Step 4: Poll for completion
        Map<String, Object> runStatus;
        String status;
        do {
            ResponseEntity<Map> statusResponse = restTemplate.exchange("https://api.openai.com/v1/threads/" + threadId + "/runs/" + runId, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            runStatus = statusResponse.getBody();
            status = (String) runStatus.get("status");
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        } while (!"completed".equals(status));

        // Step 5: Fetch the response
        ResponseEntity<Map> messagesResponse = restTemplate.exchange("https://api.openai.com/v1/threads/" + threadId + "/messages", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>) messagesResponse.getBody().get("data");
        Map<String, Object> lastMessage = data.get(0);
        List<Map<String, Object>> content = (List<Map<String, Object>>) lastMessage.get("content");
        String answer = (String) ((Map<String, Object>) content.get(0).get("text")).get("value");

        String responseId = String.valueOf(System.currentTimeMillis());
        Map<String, Object> respData = new HashMap<>();
        respData.put("answer", answer);

        if (blindMode) {
            String filename = generateSpeechToFile(answer, responseId);
            respData.put("audio_file", filename);
        }

        responses.put(responseId, respData);

        Map<String, Object> result = new HashMap<>();
        result.put("answer", answer);
        result.put("response_id", responseId);
        result.put("thread_id", threadId);

        return result;
    }

    private String generateSpeechToFile(String text, String responseId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.set("OpenAI-Beta", "assistants=v2");
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = Map.of(
                "model", "tts-1",
                "input", text,
                "voice", "onyx"
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                "https://api.openai.com/v1/audio/speech",
                HttpMethod.POST,
                request,
                byte[].class
        );

        String filename = "audio_" + responseId + ".mp3";
        String directoryPath = "audio"; // relative path; create 'audio/' folder if not exists

        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(directoryPath));
            java.nio.file.Files.write(java.nio.file.Paths.get(directoryPath, filename), response.getBody());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write audio to disk", e);
        }

        return filename;  // just filename, no full path
    }




    public ResponseEntity<ByteArrayResource> getAudio(String responseId) {
        try {
            String filename = "audio_" + responseId + ".mp3";
            Path audioPath = Paths.get("audio", filename);
            System.out.println("File ?: " + audioPath.toAbsolutePath());
            if (!Files.exists(audioPath)) {
                System.out.println("File not found: " + audioPath.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }

            ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(audioPath));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + filename)
                    .contentLength(resource.contentLength())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(500).build();
        }
    }



    public Map<String, Object> uploadFiles(List<MultipartFile> files) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.set("OpenAI-Beta", "assistants=v2");
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Step 1: Get current vector store ID from assistant
        HttpEntity<Void> configRequest = new HttpEntity<>(headers);
        ResponseEntity<Map> assistantResponse = restTemplate.exchange(
                "https://api.openai.com/v1/assistants/" + ASSISTANT_ID,
                HttpMethod.GET,
                configRequest,
                Map.class
        );

        Map<String, Object> assistantData = assistantResponse.getBody();
        Map<String, Object> toolResources = (Map<String, Object>) assistantData.get("tool_resources");
        List<String> currentVectorStoreIds = new ArrayList<>();

        if (toolResources != null && toolResources.containsKey("file_search")) {
            Map<String, Object> fileSearch = (Map<String, Object>) toolResources.get("file_search");
            currentVectorStoreIds = (List<String>) fileSearch.get("vector_store_ids");
        }

        String existingVectorStoreId = currentVectorStoreIds.isEmpty() ? null : currentVectorStoreIds.get(0);

        // Step 2: Upload new files
        List<String> uploadedFileIds = new ArrayList<>();
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                    @Override
                    public String getFilename() {
                        return file.getOriginalFilename();
                    }
                };

                body.add("file", resource);
                body.add("purpose", "assistants");

                HttpEntity<MultiValueMap<String, Object>> uploadRequest = new HttpEntity<>(body, headers);

                ResponseEntity<Map> uploadResponse = restTemplate.postForEntity(
                        "https://api.openai.com/v1/files",
                        uploadRequest,
                        Map.class
                );

                String fileId = (String) uploadResponse.getBody().get("id");
                uploadedFileIds.add(fileId);
            }
        }

        // Step 3: Fetch existing file IDs in current vector store
        List<String> allFileIds = new ArrayList<>(uploadedFileIds);

        if (existingVectorStoreId != null) {
            HttpEntity<Void> fileFetchRequest = new HttpEntity<>(headers);
            ResponseEntity<Map> existingFilesResponse = restTemplate.exchange(
                    "https://api.openai.com/v1/vector_stores/" + existingVectorStoreId + "/files",
                    HttpMethod.GET,
                    fileFetchRequest,
                    Map.class
            );

            List<Map<String, Object>> existingFiles = (List<Map<String, Object>>) existingFilesResponse.getBody().get("data");
            for (Map<String, Object> f : existingFiles) {
                allFileIds.add((String) f.get("id"));
            }
        }

        // Step 4: Create new vector store with combined file IDs
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setBearerAuth(apiKey);
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        jsonHeaders.set("OpenAI-Beta", "assistants=v2");

        Map<String, Object> createStoreBody = Map.of("file_ids", allFileIds);
        HttpEntity<Map<String, Object>> createStoreRequest = new HttpEntity<>(createStoreBody, jsonHeaders);

        ResponseEntity<Map> storeResponse = restTemplate.postForEntity(
                "https://api.openai.com/v1/vector_stores",
                createStoreRequest,
                Map.class
        );

        String newVectorStoreId = (String) storeResponse.getBody().get("id");

        // Step 5: Attach new vector store to assistant
        Map<String, Object> patchBody = Map.of(
                "tool_resources", Map.of(
                        "file_search", Map.of("vector_store_ids", List.of(newVectorStoreId))
                )
        );

        HttpEntity<Map<String, Object>> patchRequest = new HttpEntity<>(patchBody, jsonHeaders);
        restTemplate.postForEntity(
                "https://api.openai.com/v1/assistants/" + ASSISTANT_ID,
                patchRequest,
                Map.class
        );

        return Map.of(
                "message", uploadedFileIds.size() + " file(s) uploaded and assistant updated",
                "uploaded_file_ids", uploadedFileIds,
                "previous_vector_store_id", existingVectorStoreId,
                "new_vector_store_id", newVectorStoreId,
                "assistant_updated", true,
                "total_files", allFileIds.size()
        );
    }

}


