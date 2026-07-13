package com.transfer.dto;

public record CaptchaResponse(
        String captchaId,
        String imageBase64,
        Integer expireSeconds
) {
}
