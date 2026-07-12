package com.transfer.dto;

public record SliderCaptchaChallengeResponse(
        String captchaId,
        String backgroundImageBase64,
        String puzzleImageBase64,
        Integer puzzleY,
        Integer imageWidth,
        Integer imageHeight,
        Integer pieceWidth,
        Integer expireSeconds
) {
}
