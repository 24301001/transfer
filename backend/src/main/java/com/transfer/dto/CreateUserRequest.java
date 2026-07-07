package com.transfer.dto;

import com.transfer.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
        @NotBlank String fullName,
        @NotBlank String username,
        String phone,
        String email,
        @NotNull UserRole role,
        @NotBlank String password
) {
}
