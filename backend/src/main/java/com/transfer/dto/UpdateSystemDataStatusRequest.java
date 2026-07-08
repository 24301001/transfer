package com.transfer.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateSystemDataStatusRequest(
        @NotNull Boolean enabled
) {
}
