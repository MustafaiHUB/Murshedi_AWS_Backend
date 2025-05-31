package Hamza.GP.ChatBot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAIConfig {

    @Value("${openai.api.key}")
    private String openAiApiKey;

    @Bean
    public String openAiApiKey() {
        return openAiApiKey;
    }

}