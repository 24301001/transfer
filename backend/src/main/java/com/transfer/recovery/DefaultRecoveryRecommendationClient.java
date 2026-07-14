package com.transfer.recovery;

import com.transfer.dto.PredictionModuleResultRequest;
import com.transfer.model.Incident;
import com.transfer.model.IncidentAttachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

@Component
public class DefaultRecoveryRecommendationClient
        implements RecoveryRecommendationClient {

    private static final Logger log =
            LoggerFactory.getLogger(DefaultRecoveryRecommendationClient.class);

    private final RestClient restClient;
    private final boolean configured;
    private final String predictPath;
    private final String healthPath;

    public DefaultRecoveryRecommendationClient(
            @Value("${app.recovery-module.base-url:http://127.0.0.1:8003}")
            String baseUrl,

            @Value("${app.recovery-module.predict-path:/predict}")
            String predictPath,

            @Value("${app.recovery-module.health-path:/health}")
            String healthPath,

            @Value("${app.recovery-module.connect-timeout:5000}")
            int connectTimeout,

            @Value("${app.recovery-module.read-timeout:60000}")
            int readTimeout
    ) {
        String trimmedBaseUrl =
                baseUrl == null ? "" : baseUrl.trim();

        this.configured = !trimmedBaseUrl.isBlank();
        this.predictPath = normalizePath(predictPath, "/predict");
        this.healthPath = normalizePath(healthPath, "/health");

        if (configured) {
            SimpleClientHttpRequestFactory requestFactory =
                    new SimpleClientHttpRequestFactory();

            requestFactory.setConnectTimeout(
                    Math.max(500, connectTimeout)
            );
            requestFactory.setReadTimeout(
                    Math.max(500, readTimeout)
            );

            this.restClient =
                    RestClient.builder()
                            .baseUrl(trimTrailingSlash(trimmedBaseUrl))
                            .requestFactory(requestFactory)
                            .defaultHeaders(headers ->
                                    headers.setContentType(MediaType.APPLICATION_JSON)
                            )
                            .build();

            log.info(
                    "Algorithm3 recovery client configured: baseUrl={}, predictPath={}, healthPath={}",
                    trimmedBaseUrl,
                    this.predictPath,
                    this.healthPath
            );
        } else {
            this.restClient = null;
            log.warn("Algorithm3 recovery client is not configured.");
        }
    }

    @Override
    public Optional<RecoveryRecommendationResult> recommend(
            Incident incident,
            List<IncidentAttachment> attachments,
            PredictionModuleResultRequest predictionResult
    ) {
        if (!configured || restClient == null || incident == null || predictionResult == null) {
            return Optional.empty();
        }

        try {
            RecoveryPredictRequest request =
                    RecoveryPredictRequest.from(
                            incident,
                            attachments,
                            predictionResult
                    );

            RecoveryPredictResponse response =
                    restClient
                            .post()
                            .uri(predictPath)
                            .body(request)
                            .retrieve()
                            .body(RecoveryPredictResponse.class);

            if (response == null) {
                log.warn("Algorithm3 recovery response is empty: incidentId={}", incident.getId());
                return Optional.empty();
            }

            if (!"COMPLETED".equals(response.status()) || response.result() == null) {
                log.warn(
                        "Algorithm3 recovery did not complete: incidentId={}, status={}, error={}",
                        incident.getId(),
                        response.status(),
                        response.errorMessage()
                );
                return Optional.empty();
            }

            RecoveryPredictionPayload payload =
                    response.result();

            return Optional.of(
                    new RecoveryRecommendationResult(
                            payload.predictedRecoveryDurationMinutes(),
                            payload.recoveryLevel(),
                            payload.longRecoveryProbability(),
                            payload.classificationThresholdMinutes(),
                            payload.confidence(),
                            payload.modelVersion(),
                            payload.recommendation(),
                            payload.keyFactors(),
                            payload.metrics(),
                            payload.features(),
                            response.traceId()
                    )
            );
        } catch (RestClientException ex) {
            log.warn(
                    "Algorithm3 recovery request failed: incidentId={}, error={}",
                    incident.getId(),
                    ex.getMessage()
            );
            return Optional.empty();
        }
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public boolean healthCheck() {
        if (!configured || restClient == null) {
            return false;
        }

        try {
            restClient
                    .get()
                    .uri(healthPath)
                    .retrieve()
                    .toBodilessEntity();

            return true;
        } catch (RestClientException ex) {
            log.debug("Algorithm3 recovery health check failed: {}", ex.getMessage());
            return false;
        }
    }

    private static String normalizePath(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private record RecoveryPredictRequest(
            Long incidentId,
            String incidentNo,
            String description,
            String accidentType,
            String riskLevel,
            Double riskScore,
            Integer congestionDurationMinutes,
            Double longitude,
            Double latitude,
            Integer occupiedLanes,
            Integer trafficFlow,
            Integer peopleFlow,
            String roadLevel,
            String roadStatus,
            String weather,
            String sceneLabels,
            String initialAccidentType,
            List<RecoveryAttachmentPayload> attachments
    ) {
        static RecoveryPredictRequest from(
                Incident incident,
                List<IncidentAttachment> attachments,
                PredictionModuleResultRequest predictionResult
        ) {
            return new RecoveryPredictRequest(
                    incident.getId(),
                    incident.getIncidentNo(),
                    incident.getDescription(),
                    predictionResult.accidentType(),
                    predictionResult.riskLevel() == null
                            ? null
                            : predictionResult.riskLevel().name(),
                    predictionResult.riskScore(),
                    predictionResult.congestionDurationMinutes(),
                    incident.getLongitude(),
                    incident.getLatitude(),
                    incident.getOccupiedLanes(),
                    incident.getTrafficFlow(),
                    incident.getPeopleFlow(),
                    incident.getRoadLevel(),
                    incident.getRoadStatus(),
                    incident.getWeather(),
                    incident.getSceneLabels(),
                    incident.getInitialAccidentType(),
                    attachments == null
                            ? List.of()
                            : attachments.stream()
                            .map(RecoveryAttachmentPayload::from)
                            .toList()
            );
        }
    }

    private record RecoveryAttachmentPayload(
            Long id,
            String originalFilename,
            String contentType,
            Long fileSize,
            String filePath,
            String aiDetectedTypes
    ) {
        static RecoveryAttachmentPayload from(
                IncidentAttachment attachment
        ) {
            return new RecoveryAttachmentPayload(
                    attachment.getId(),
                    attachment.getOriginalFilename(),
                    attachment.getContentType(),
                    attachment.getFileSize(),
                    attachment.getFilePath(),
                    attachment.getAiDetectedTypes()
            );
        }
    }

    private record RecoveryPredictResponse(
            String traceId,
            String status,
            RecoveryPredictionPayload result,
            String errorMessage
    ) {
    }

    private record RecoveryPredictionPayload(
            Integer predictedRecoveryDurationMinutes,
            String recoveryLevel,
            Double longRecoveryProbability,
            Integer classificationThresholdMinutes,
            Double confidence,
            String modelVersion,
            String recommendation,
            List<String> keyFactors,
            Object metrics,
            Object features
    ) {
    }
}
