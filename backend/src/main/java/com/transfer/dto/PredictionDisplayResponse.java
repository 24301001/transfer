package com.transfer.dto;

import java.time.LocalDateTime;

import com.transfer.enums.RiskLevel;
import com.transfer.model.Incident;
import com.transfer.model.PredictionResult;

public record PredictionDisplayResponse(
        Long incidentId,
        String incidentNo,
        String locationName,
        String roadName,
        String roadStatus,
        String weather,
        Integer occupiedLanes,
        Integer trafficFlow,
        Integer peopleFlow,
        String accidentType,
        RiskLevel riskLevel,
        Double riskScore,
        Integer congestionDurationMinutes,
        Integer recoveryDurationMinutes,
        Double confidence,
        String modelVersion,
        String suggestions,
        String explanation,
        String riskFactors,
        String imageEvidence,
        String evidenceSummary,
        String dataModuleTraceId,
        String recoveryRecommendation,
        Double recoveryConfidence,
        String recoveryLevel,
        String recoveryModelVersion,
        String recoveryTraceId,
        String recoveryKeyFactors,
        LocalDateTime generatedAt
) {
    public static PredictionDisplayResponse from(Incident incident, PredictionResult result) {
        return new PredictionDisplayResponse(
                incident.getId(),
                incident.getIncidentNo(),
                incident.getLocationName(),
                incident.getRoadName(),
                incident.getRoadStatus(),
                incident.getWeather(),
                incident.getOccupiedLanes(),
                incident.getTrafficFlow(),
                incident.getPeopleFlow(),
                result.getAccidentType(),
                result.getRiskLevel(),
                result.getRiskScore(),
                result.getCongestionDurationMinutes(),
                result.getRecoveryDurationMinutes(),
                result.getConfidence(),
                result.getModelVersion(),
                result.getSuggestions(),
                result.getExplanation(),
                result.getRiskFactors(),
                result.getImageEvidence(),
                result.getEvidenceSummary(),
                result.getDataModuleTraceId(),
                result.getRecoveryRecommendation(),
                result.getRecoveryConfidence(),
                result.getRecoveryLevel(),
                result.getRecoveryModelVersion(),
                result.getRecoveryTraceId(),
                result.getRecoveryKeyFactors(),
                result.getCreatedAt()
        );
    }
}
