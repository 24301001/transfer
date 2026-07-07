package com.transfer.dto;

import com.transfer.enums.TaskType;
import jakarta.validation.constraints.NotNull;

public record CreateDispatchTaskRequest(
        @NotNull Long incidentId,
        @NotNull TaskType taskType,
        Long receiverUserId,
        Long assignedByUserId,
        Boolean vehicleRequired,
        String vehicleType,
        String advice
) {
}
