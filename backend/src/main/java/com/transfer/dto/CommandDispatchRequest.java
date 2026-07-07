package com.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CommandDispatchRequest(
        @NotNull
        Long assignedByUserId,

        @NotEmpty
        List<@Valid DispatchAssignmentRequest> assignments,

        @Size(max = 1000)
        String commonAdvice
) {
}
