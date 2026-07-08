package com.transfer.dto;

import com.transfer.enums.IncidentStatus;
import com.transfer.enums.RiskLevel;
import com.transfer.model.Incident;
import com.transfer.model.PredictionResult;

import java.time.LocalDateTime;

public record IncidentHistoryResponse(
        Long id,
        String incidentNo,
        String locationName,
        String address,
        String roadName,
        String initialAccidentType,
        String confirmedAccidentType,
        String description,
        IncidentStatus status,
        RiskLevel riskLevel,
        Integer predictedCongestionMinutes,
        Integer predictedRecoveryMinutes,
        Double confidence,
        Boolean supportRequired,
        Boolean casualtyDetected,
        Long reportUserId,
        Long dispatchTaskCount,
        String latestPredictionModelVersion,
        String latestPredictionRiskFactors,
        String latestPredictionEvidenceSummary,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static IncidentHistoryResponse from(
            Incident incident,
            PredictionResult latestPrediction,
            long dispatchTaskCount
    ) {
        return new IncidentHistoryResponse(
                incident.getId(),
                incident.getIncidentNo(),
                incident.getLocationName(),
                incident.getAddress(),
                incident.getRoadName(),
                incident.getInitialAccidentType(),
                incident.getConfirmedAccidentType(),
                incident.getDescription(),
                incident.getStatus(),
                incident.getRiskLevel(),
                incident.getPredictedCongestionMinutes(),
                incident.getPredictedRecoveryMinutes(),
                incident.getConfidence(),
                incident.getSupportRequired(),
                incident.getCasualtyDetected(),
                incident.getReportUserId(),
                dispatchTaskCount,
                latestPrediction == null ? null : latestPrediction.getModelVersion(),
                latestPrediction == null ? null : latestPrediction.getRiskFactors(),
                latestPrediction == null ? null : latestPrediction.getEvidenceSummary(),
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }
}
