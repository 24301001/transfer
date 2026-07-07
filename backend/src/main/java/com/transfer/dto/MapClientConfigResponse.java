package com.transfer.dto;

public record MapClientConfigResponse(
        boolean enabled,
        String browserAk,
        String scriptUrl,
        String message
) {
}

