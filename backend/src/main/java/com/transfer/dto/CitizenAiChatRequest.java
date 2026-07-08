package com.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CitizenAiChatRequest(
        Long incidentId,

        @NotBlank
        @Size(max = 300)
        String question,

        @Size(max = 160)
        String locationName,

        @Size(max = 1000)
        String description
) {
}
