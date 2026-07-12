package com.transfer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SliderCaptchaVerifyRequest(
        @NotBlank String captchaId,
        @NotNull @Min(0) Integer sliderX
) {
}
