package com.transfer.dto;

public record IncidentArrivalEstimateResponse(
        Long incidentId,
        Integer estimatedPoliceArrivalMinutes,
        String estimatedPoliceArrivalText
) {
}
