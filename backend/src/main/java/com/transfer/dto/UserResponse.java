package com.transfer.dto;

import com.transfer.enums.UserRole;
import com.transfer.enums.UserStatus;
import com.transfer.model.UserAccount;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String fullName,
        String username,
        String phone,
        String email,
        UserRole role,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getPhone(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
