package com.transfer.dto;

import com.transfer.enums.UserRole;
import com.transfer.enums.UserStatus;
import com.transfer.model.UserAccount;

public record CurrentUserResponse(
        Long id,
        String fullName,
        String username,
        String phone,
        String email,
        Boolean emailVerified,
        UserRole role,
        UserStatus status
) {
    public static CurrentUserResponse from(UserAccount user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getPhone(),
                user.getEmail(),
                user.getEmailVerified(),
                user.getRole(),
                user.getStatus()
        );
    }
}
