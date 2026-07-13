package com.transfer.security;

import com.transfer.common.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

public final class RequestSecurityAttributes {

    public static final String AUTHENTICATED_USER =
            RequestSecurityAttributes.class.getName() + ".AUTHENTICATED_USER";

    private RequestSecurityAttributes() {
    }

    public static AuthenticatedUser requireAuthenticatedUser(HttpServletRequest request) {
        Object value = request.getAttribute(AUTHENTICATED_USER);
        if (value instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }
        throw new UnauthorizedException("Authentication is required");
    }
}
