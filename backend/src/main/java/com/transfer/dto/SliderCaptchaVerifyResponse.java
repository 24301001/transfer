package com.transfer.dto;

public record SliderCaptchaVerifyResponse(
        String sliderToken,
        Integer expireSeconds
) {
}
