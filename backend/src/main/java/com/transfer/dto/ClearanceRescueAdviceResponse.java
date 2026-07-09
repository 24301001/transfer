package com.transfer.dto;

import com.transfer.enums.AdviceReviewStatus;
import com.transfer.model.ClearanceRescueAdvice;

import java.time.LocalDateTime;

public record ClearanceRescueAdviceResponse(
        Long id,
        Long incidentId,
        Long predictionResultId,

        String aiAdvice,
        String finalAdvice,

        AdviceReviewStatus status,
        String generationSource,

        Boolean modifiedByCommand,

        Long confirmedByUserId,
        LocalDateTime confirmedAt,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ClearanceRescueAdviceResponse from(
            ClearanceRescueAdvice advice
    ) {
        return new ClearanceRescueAdviceResponse(
                advice.getId(),
                advice.getIncidentId(),
                advice.getPredictionResultId(),

                advice.getAiAdvice(),
                advice.getFinalAdvice(),

                advice.getStatus(),
                advice.getGenerationSource(),

                advice.getModifiedByCommand(),

                advice.getConfirmedByUserId(),
                advice.getConfirmedAt(),

                advice.getCreatedAt(),
                advice.getUpdatedAt()
        );
    }
}
