package com.transfer.dispatch;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.transfer.dto.PredictionModuleResultRequest;
import com.transfer.model.Incident;
import com.transfer.model.IncidentAttachment;

@Component
public class DefaultDispatchRecommendationClient
        implements DispatchRecommendationClient {

    private static final Logger log =
            LoggerFactory.getLogger(DefaultDispatchRecommendationClient.class);

    private final RestClient restClient;
    private final boolean configured;
    private final String predictPath;
    private final String healthPath;

    public DefaultDispatchRecommendationClient(
            @Value("${app.dispatch-module.base-url:http://127.0.0.1:8004}")
            String baseUrl,

            @Value("${app.dispatch-module.predict-path:/predict}")
            String predictPath,

            @Value("${app.dispatch-module.health-path:/health}")
            String healthPath,

            @Value("${app.dispatch-module.connect-timeout:5000}")
            int connectTimeout,

            @Value("${app.dispatch-module.read-timeout:30000}")
            int readTimeout
    ) {
        String trimmedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.configured = !trimmedBaseUrl.isBlank();
        this.predictPath = normalizePath(predictPath, "/predict");
        this.healthPath = normalizePath(healthPath, "/health");

        if (configured) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Math.max(500, connectTimeout));
            requestFactory.setReadTimeout(Math.max(500, readTimeout));

            this.restClient = RestClient.builder()
                    .baseUrl(trimTrailingSlash(trimmedBaseUrl))
                    .requestFactory(requestFactory)
                    .defaultHeaders(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
                    .build();

            log.info("Algorithm4 dispatch client configured: baseUrl={}, predictPath={}",
                    trimmedBaseUrl, this.predictPath);
        } else {
            this.restClient = null;
            log.warn("Algorithm4 dispatch client is not configured.");
        }
    }

    @Override
    public Optional<DispatchRecommendationResult> recommend(
            Incident incident,
            List<IncidentAttachment> attachments,
            PredictionModuleResultRequest predictionResult
    ) {
        if (!configured || restClient == null || incident == null || predictionResult == null) {
            return Optional.empty();
        }

        try {
            DispatchPredictRequest request = DispatchPredictRequest.from(
                    incident, predictionResult);

            DispatchPredictResponse response = restClient
                    .post()
                    .uri(predictPath)
                    .body(request)
                    .retrieve()
                    .body(DispatchPredictResponse.class);

            if (response == null || !"COMPLETED".equals(response.status())
                    || response.result() == null) {
                log.warn("Algorithm4 dispatch incomplete: incidentId={}, status={}",
                        incident.getId(), response != null ? response.status() : "null");
                return Optional.empty();
            }

            DispatchPayload payload = response.result();

            List<DispatchRecommendationResult.DispatchItem> items =
                    payload.dispatchPlan() == null ? List.of() :
                    payload.dispatchPlan().stream()
                            .map(p -> new DispatchRecommendationResult.DispatchItem(
                                    p.taskType(), p.priority(), p.recommendedUnits(),
                                    p.estimatedArrivalMinutes(), p.reasoning(), p.score()))
                            .toList();

            return Optional.of(new DispatchRecommendationResult(
                    items,
                    payload.stateVector(),
                    payload.modelVersion(),
                    response.traceId()
            ));
        } catch (RestClientException ex) {
            log.warn("Algorithm4 dispatch request failed: incidentId={}, error={}",
                    incident.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public boolean healthCheck() {
        if (!configured || restClient == null) return false;
        try {
            restClient.get().uri(healthPath).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            log.debug("Algorithm4 health check failed: {}", ex.getMessage());
            return false;
        }
    }

    private static String normalizePath(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String trimmed = value.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/") && trimmed.length() > 1)
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    // ── internal records ──

    private record DispatchPredictRequest(
            Long incidentId, String incidentNo, String description,
            String accidentType, String riskLevel, Double riskScore,
            Integer congestionDurationMinutes, Integer recoveryDurationMinutes,
            Double longitude, Double latitude,
            Integer occupiedLanes, Integer trafficFlow, Integer peopleFlow,
            String roadLevel, String roadStatus, String weather,
            Boolean injuryReported, Integer injuredCount,
            Boolean casualtyDetected, Double confidence
    ) {
        static DispatchPredictRequest from(
                Incident incident,
                PredictionModuleResultRequest pr
        ) {
            return new DispatchPredictRequest(
                    incident.getId(), incident.getIncidentNo(),
                    incident.getDescription(),
                    pr.accidentType(),
                    pr.riskLevel() == null ? null : pr.riskLevel().name(),
                    pr.riskScore(),
                    pr.congestionDurationMinutes(),
                    pr.recoveryDurationMinutes(),
                    incident.getLongitude(), incident.getLatitude(),
                    incident.getOccupiedLanes(), incident.getTrafficFlow(),
                    incident.getPeopleFlow(),
                    incident.getRoadLevel(), incident.getRoadStatus(),
                    incident.getWeather(),
                    incident.getInjuryReported(), incident.getInjuredCount(),
                    incident.getCasualtyDetected(), pr.confidence()
            );
        }
    }

    private record DispatchPredictResponse(
            String traceId, String status,
            DispatchPayload result, String errorMessage
    ) {}

    private record DispatchPayload(
            List<DispatchPlanItem> dispatchPlan,
            List<Double> stateVector,
            String modelVersion
    ) {}

    private record DispatchPlanItem(
            String taskType, String priority,
            int recommendedUnits, int estimatedArrivalMinutes,
            String reasoning, double score
    ) {}
}
