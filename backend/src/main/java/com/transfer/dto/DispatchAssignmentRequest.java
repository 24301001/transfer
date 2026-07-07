package com.transfer.dto;

import com.transfer.enums.TaskType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DispatchAssignmentRequest(
        @NotNull
        TaskType taskType,

        @NotNull
        Long receiverUserId,

        Boolean vehicleRequired,

        @Size(max = 80)
        String vehicleType,

        @Size(max = 1000)
        String advice
) {
}
