package com.transfer.dto;

public record EmailCodeResponse(
        String message,
        Integer expireSeconds,
        String devCode
) {
}
