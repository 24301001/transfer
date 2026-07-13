package com.transfer.dto;

import com.transfer.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 64) String fullName,
        @NotBlank @Size(min = 3, max = 64) String username,
        @Size(max = 32) String phone,
        @NotBlank @Email @Size(max = 128) String email,
        UserRole role,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank String emailCode
) {
}
