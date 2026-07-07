package com.transfer.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateIncidentRequest(
        @NotBlank String locationName,
        String address,
        Double longitude,
        Double latitude,
        String roadName,
        String initialAccidentType,
        @NotBlank String description,
        Integer occupiedLanes,
        Integer trafficFlow,
        String weather,
        String roadLevel,
        Long reportUserId
) {
}
