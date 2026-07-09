package com.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(min = 6, max = 128) String oldPassword,
        @NotBlank @Size(min = 8, max = 128) String newPassword,
        @NotBlank String emailCode,
        @NotBlank String captchaId,
        @NotBlank String captchaCode
) {
}
