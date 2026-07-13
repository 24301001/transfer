package com.transfer.dto;

public record LoginResponse(
        String token,
        String tokenType,
        Long expiresInSeconds,
        CurrentUserResponse user
) {
}
