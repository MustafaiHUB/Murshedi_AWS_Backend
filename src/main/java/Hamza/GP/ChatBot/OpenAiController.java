package Hamza.GP.ChatBot;

import Hamza.GP.Appuser.UserRepository;
import Hamza.GP.Authentication.JwtUtil;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OpenAiController {

    private final QuestionAnswerService assistantService;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;
    private final String apiKey;

    public OpenAiController(QuestionAnswerService assistantService, JwtUtil jwtUtil, RestTemplate restTemplate, String apiKey) {
        this.assistantService = assistantService;
        this.jwtUtil = jwtUtil;
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    @PostMapping("/ask")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<Map<String, String>> askQuestion(@RequestBody Map<String, String> request, @RequestHeader("Authorization") String token) {
        try {
            String userQuestion = request.get("question");
            String threadId = request.get("thread_id");
            String conversationId = request.getOrDefault("conversationId", null);

            if (userQuestion == null || userQuestion.isEmpty()) {
                return ResponseEntity.badRequest().body(null);
            }

            String email = jwtUtil.extractEmail(token.substring(7));

            // Fetch assistant config to match SDK behavior
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            headers.set("OpenAI-Beta", "assistants=v2");

            HttpEntity<Void> configRequest = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.openai.com/v1/assistants/asst_1GORjnI6QmuaZOBIqQEKUela",
                    HttpMethod.GET,
                    configRequest,
                    Map.class
            );

            Map<String, Object> assistantConfig = response.getBody();
            Map<String, Object> toolResources = (Map<String, Object>) assistantConfig.get("tool_resources");
            List<String> vectorStoreIds = null;

            if (toolResources != null && toolResources.containsKey("file_search")) {
                Map<String, Object> fileSearch = (Map<String, Object>) toolResources.get("file_search");
                vectorStoreIds = (List<String>) fileSearch.get("vector_store_ids");
            }

            return ResponseEntity.ok(
                    assistantService.getAnswer(userQuestion, conversationId, email, threadId, vectorStoreIds)
            );
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/audio/{responseId}")
    public ResponseEntity<ByteArrayResource> getAudio(@PathVariable String responseId) {
        System.out.println(responseId);
        return assistantService.getAudio(responseId);
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("files") List<MultipartFile> files) {
        try {
            return ResponseEntity.ok(assistantService.uploadFiles(files));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Void> statusCheck() {
        return ResponseEntity.ok().build();
    }
}
