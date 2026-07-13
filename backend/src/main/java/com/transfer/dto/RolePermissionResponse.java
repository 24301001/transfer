package com.transfer.dto;

import com.transfer.enums.UserRole;

import java.util.List;

public record RolePermissionResponse(
        UserRole role,
        String displayName,
        List<String> permissions
) {
}
