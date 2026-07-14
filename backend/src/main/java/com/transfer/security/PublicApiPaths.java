package com.transfer.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Central registry for anonymous API paths (login, register, slider captcha, etc.).
 */
public final class PublicApiPaths {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<String> PATTERNS = List.of(
            "/api/health",
            "/api/v1/health",
            "/api/v1/auth/slider-captcha/**",
            "/api/v1/auth/email-code",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/password/reset",
            "/api/v1/incidents/public-report",
            "/api/v1/incidents/public/*/prediction-status",
            "/api/v1/report-ai/**",
            "/api/v1/maps/client-config",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/h2-console/**",
            "/slider-captcha-demo.html",
            "/js/**",
            "/css/**",
            "/favicon.ico"
    );

    private PublicApiPaths() {
    }

    public static boolean matches(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return matches(resolvePath(request));
    }

    public static boolean matches(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = normalize(path);
        return PATTERNS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, normalized));
    }

    public static String resolvePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return normalize(uri);
    }

    private static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String normalized = path;
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? "/" : normalized;
    }
}
