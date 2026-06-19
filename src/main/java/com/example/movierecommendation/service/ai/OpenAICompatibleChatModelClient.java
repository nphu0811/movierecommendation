package com.example.movierecommendation.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAICompatibleChatModelClient implements ChatModelClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleChatModelClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${openai.api.key:}")
    private String legacyApiKey;

    @Value("${ai.chat-model:gpt-4o-mini}")
    private String chatModel;

    @Value("${ai.timeout-seconds:8}")
    private long timeoutSeconds;

    private volatile WebClient webClient;

    @Override
    public boolean isEnabled() {
        String url = clean(baseUrl);
        String key = effectiveApiKey();
        boolean localProvider = url.contains("localhost") || url.contains("127.0.0.1") || url.contains("host.docker.internal");
        return !url.isEmpty() && (!key.isEmpty() || localProvider);
    }

    @Override
    public String complete(List<Map<String, Object>> messages, Map<String, Object> responseFormat,
                           int maxTokens, double temperature) {
        if (!isEnabled()) return null;

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", chatModel);
            body.put("messages", messages);
            body.put("max_tokens", maxTokens);
            body.put("temperature", temperature);
            if (responseFormat != null && !responseFormat.isEmpty()) {
                body.put("response_format", responseFormat);
            }

            String response = client().post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

            if (response == null) return null;
            JsonNode root = mapper.readTree(response);
            String content = root.at("/choices/0/message/content").asText("").trim();
            return content.isEmpty() ? null : content;
        } catch (Exception e) {
            log.warn("Chat model request failed: {}", e.getMessage());
            return null;
        }
    }

    private WebClient client() {
        if (webClient == null) {
            synchronized (this) {
                if (webClient == null) {
                    WebClient.Builder builder = WebClient.builder()
                        .baseUrl(clean(baseUrl))
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    String key = effectiveApiKey();
                    if (!key.isEmpty()) {
                        builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key);
                    }
                    webClient = builder.build();
                }
            }
        }
        return webClient;
    }

    private String clean(String value) {
        return value == null ? "" : value.replace("\"", "").trim().replaceAll("/+$", "");
    }

    private String effectiveApiKey() {
        String configured = clean(apiKey);
        return configured.isEmpty() ? clean(legacyApiKey) : configured;
    }
}
