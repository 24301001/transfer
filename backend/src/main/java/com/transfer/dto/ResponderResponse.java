package com.transfer.dto;

import com.transfer.enums.UserRole;

public record ResponderResponse(
        Long id,
        String fullName,
        String username,
        String phone,
        UserRole role
) {
}
