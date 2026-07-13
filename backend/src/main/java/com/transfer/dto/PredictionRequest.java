package com.transfer.dto;

import java.util.List;

public record PredictionRequest(
        Long incidentId,
        String incidentNo,
        String locationName,
        String address,
        Double longitude,
        Double latitude,
        String roadName,
        String accidentType,
        String description,
        Integer occupiedLanes,
        Integer trafficFlow,
        String weather,
        String roadLevel,
        List<PredictionAttachmentPayload> attachments
) {
}