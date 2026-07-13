package com.transfer.security;

import com.transfer.enums.UserRole;
import com.transfer.model.UserAccount;

public record AuthenticatedUser(
        Long userId,
        String username,
        UserRole role,
        String tokenId,
        UserAccount user
) {
}
