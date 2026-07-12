package com.transfer.security;

import com.transfer.common.ForbiddenException;
import com.transfer.common.UnauthorizedException;
import com.transfer.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class RoleAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        // 匿名路径直接放行——不需要认证也不需要角色检查
        if (PublicApiPaths.matches(request)) {
            return true;
        }

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRoles requireRoles = handlerMethod.getMethodAnnotation(RequireRoles.class);
        if (requireRoles == null) {
            requireRoles = handlerMethod.getBeanType().getAnnotation(RequireRoles.class);
        }
        if (requireRoles == null) {
            return true;
        }

        Object value = request.getAttribute(RequestSecurityAttributes.AUTHENTICATED_USER);
        if (!(value instanceof AuthenticatedUser authenticatedUser)) {
            throw new UnauthorizedException("Authentication is required");
        }

        UserRole role = authenticatedUser.role();
        boolean allowed = Arrays.stream(requireRoles.value()).anyMatch(role::equals);
        if (!allowed) {
            throw new ForbiddenException("当前账号无权访问该接口");
        }
        return true;
    }
}
