package com.transfer.dto;

import com.transfer.enums.VerificationPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmailCodeRequest(
        @NotNull VerificationPurpose purpose,
        @Size(max = 64) String username,
        @Email @Size(max = 128) String email,
        @NotBlank String sliderToken
) {
}
