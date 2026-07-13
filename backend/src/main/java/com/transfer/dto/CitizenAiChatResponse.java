package com.transfer.dto;

public record CitizenAiChatResponse(
        String reply,
        Boolean outOfScope,
        Boolean casualtyDetected,
        Boolean call120Required,
        String emergencyPhone
) {
}
