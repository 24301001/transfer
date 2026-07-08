package com.transfer.dto;

public record PublicIncidentSubmitResponse(
        IncidentDetailResponse incidentDetail,
        CitizenImmediateAdviceResponse immediateAdvice,
        Integer estimatedPoliceArrivalMinutes,
        String estimatedPoliceArrivalText,
        PredictionSubmitResponse predictionSubmit
) {
}
