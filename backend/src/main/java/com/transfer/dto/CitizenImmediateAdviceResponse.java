package com.transfer.dto;

import java.util.List;

public record CitizenImmediateAdviceResponse(
        String calmingMessage,
        String immediateAdvice,
        List<String> actionItems,
        Boolean casualtyDetected,
        Boolean call120Required,
        String emergencyPhone,
        Boolean aiGenerated
) {
}
