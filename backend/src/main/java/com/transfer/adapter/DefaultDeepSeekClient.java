package com.transfer.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class DefaultDeepSeekClient implements DeepSeekClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.siliconflow.api-url:https://api.siliconflow.cn/v1/chat/completions}")
    private String apiUrl;

    @Value("${app.siliconflow.api-key:}")
    private String apiKey;

    @Value("${app.siliconflow.model:Qwen/Qwen2.5-7B-Instruct}")
    private String model;

    @Override
    public String explain(PredictionOutcome outcome, String locationName, String description) {
        if (apiKey == null || apiKey.isBlank()) {
            return fallbackExplain(outcome, locationName);
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
                                    "content", "你是交通事故风险预测结果说明生成助手。请根据结构化预测结果，生成简洁、客观、适合指挥人员查看的中文自然语言解释。只解释模型预测结论和依据，不输出调度方案或处置建议，不编造未提供的信息。"
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", buildPrompt(outcome, locationName, description)
                            )
                    ),
                    "temperature", 0.2,
                    "max_tokens", 500,
                    "stream", false
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

            return parseContent(response.getBody(), outcome, locationName);
        } catch (RestClientException ex) {
            return fallbackExplain(outcome, locationName);
        }
    }

    private String buildPrompt(PredictionOutcome outcome, String locationName, String description) {
        return """
                请将以下交通事故模型预测结果转换为自然语言解释，要求：
                1. 说明事故类型、风险等级、风险分数或可信度、预计拥堵持续时间、预计道路恢复时间；
                2. 解释这些预测结论主要由哪些风险因素、现场信息或证据支持；
                3. 只解释模型结果，不输出调度方案、清障方案、处置步骤或“建议调度某车辆”等内容；
                4. 语气专业、简洁，适合指挥中心人员查看；
                5. 不要输出 JSON、Markdown 表格或代码块。

                事故地点：%s
                事故描述：%s
                事故类型：%s
                风险等级：%s
                拥堵持续时间：%s 分钟
                道路恢复时间：%s 分钟
                可信度：%s
                模型版本：%s
                风险因素：%s
                证据摘要：%s
                """.formatted(
                safe(locationName),
                safe(description),
                safe(outcome.accidentType()),
                outcome.riskLevel(),
                outcome.congestionDurationMinutes(),
                outcome.recoveryDurationMinutes(),
                outcome.confidence(),
                safe(outcome.modelVersion()),
                outcome.riskFactors() == null ? "未提供" : outcome.riskFactors(),
                safe(outcome.evidenceSummary())
        );
    }

    private String parseContent(String responseBody, PredictionOutcome outcome, String locationName) {
        if (responseBody == null || responseBody.isBlank()) {
            return fallbackExplain(outcome, locationName);
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            String content = contentNode.asText();
            if (content == null || content.isBlank()) {
                return fallbackExplain(outcome, locationName);
            }
            return content.trim();
        } catch (Exception ex) {
            return fallbackExplain(outcome, locationName);
        }
    }

    private String fallbackExplain(PredictionOutcome outcome, String locationName) {
        return "事故地点：" + safe(locationName)
                + "；系统接收的数据预测结果显示，事故类型为“" + safe(outcome.accidentType())
                + "”，风险等级为“" + outcome.riskLevel()
                + "”。预计拥堵持续约 "
                + outcome.congestionDurationMinutes()
                + " 分钟，道路恢复正常通行约需 "
                + outcome.recoveryDurationMinutes()
                + " 分钟，模型可信度为 "
                + outcome.confidence()
                + "。主要风险因素："
                + (outcome.riskFactors() == null || outcome.riskFactors().isEmpty()
                ? "未提供"
                : outcome.riskFactors())
                + "。";
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "未提供" : value;
    }
}