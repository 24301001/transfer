package com.transfer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SupportDecisionRequest(
        @NotNull
        Boolean supportRequired,

        @Size(max = 500)
        String reason,

        Long decidedByUserId
) {
}
