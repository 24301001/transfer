package com.transfer.dto;

import jakarta.validation.constraints.NotBlank;

public record ProfileEmailCodeRequest(
        @NotBlank String captchaId,
        @NotBlank String captchaCode
) {
}
