package com.transfer.dto;

public record PredictionRequest(
        Long incidentId,
        String locationName,
        String roadName,
        String accidentType,
        String description,
        Integer occupiedLanes,
        Integer trafficFlow,
        String weather,
        String roadLevel
) {
}
