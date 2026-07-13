package com.transfer.dto;

public record PredictionSubmitResponse(
        Boolean submitted,
        String status,
        String message,
        String dataModuleTraceId,
        PredictionRequest payload,
        String responseBody
) {
}