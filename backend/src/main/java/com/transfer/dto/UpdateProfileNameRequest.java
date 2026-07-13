package com.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileNameRequest(
        @NotBlank @Size(max = 64) String fullName
) {
}
