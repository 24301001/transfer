package com.transfer.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class SiliconFlowClient {

    private static final Logger log =
            LoggerFactory.getLogger(SiliconFlowClient.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public SiliconFlowClient(
            @Value("${app.siliconflow.api-url:https://api.siliconflow.cn/v1/chat/completions}")
            String apiUrl,

            @Value("${app.siliconflow.api-key:}")
            String apiKey,

            @Value("${app.siliconflow.model:Qwen/Qwen2.5-7B-Instruct}")
            String model
    ) {
        this.apiUrl = apiUrl == null ? "" : apiUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
    }

    public boolean configured() {
        return !apiUrl.isBlank()
                && !apiKey.isBlank()
                && !model.isBlank();
    }

    public String chat(
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            double temperature
    ) {
        if (!configured()) {
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", safe(systemPrompt)
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", safe(userPrompt)
                            )
                    ),
                    "temperature", temperature,
                    "max_tokens", maxTokens,
                    "stream", false
            );

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            apiUrl,
                            entity,
                            String.class
                    );

            return parseContent(response.getBody());

        } catch (RestClientException ex) {
            log.warn("调用硅基流动失败: {}", ex.getMessage());
            return null;
        }
    }

    private String parseContent(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();

            if (content == null || content.isBlank()) {
                return null;
            }

            return content.trim();
        } catch (Exception ex) {
            log.warn("解析硅基流动响应失败: {}", ex.getMessage());
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
