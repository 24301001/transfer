package com.transfer.dto;

import com.transfer.enums.UserRole;
import com.transfer.enums.UserStatus;

public record UpdateUserRequest(
        String fullName,
        String username,
        String phone,
        String email,
        UserRole role,
        UserStatus status,
        String password
) {
}
