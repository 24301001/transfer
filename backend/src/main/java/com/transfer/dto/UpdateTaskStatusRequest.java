package com.transfer.dto;

import com.transfer.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(
        @NotNull TaskStatus status,
        String feedback
) {
}
