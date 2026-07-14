package com.transfer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transfer.common.ErrorResponse;
import com.transfer.common.ServiceUnavailableException;
import com.transfer.common.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/health",
            "/api/v1/health",
            "/api/v1/auth/slider-captcha/challenge",
            "/api/v1/auth/slider-captcha/verify",
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

    private final JwtAuthenticationService authenticationService;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(
            JwtAuthenticationService authenticationService,
            ObjectMapper objectMapper
    ) {
        this.authenticationService = authenticationService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/")) {
            return true;
        }
        return PUBLIC_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            AuthenticatedUser authenticatedUser = authenticationService.authenticate(token);
            request.setAttribute(RequestSecurityAttributes.AUTHENTICATED_USER, authenticatedUser);
            filterChain.doFilter(request, response);
        } catch (ServiceUnavailableException ex) {
            writeError(response, request, HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        } catch (UnauthorizedException ex) {
            writeError(response, request, HttpStatus.UNAUTHORIZED, ex.getMessage());
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }

        // Native EventSource cannot set Authorization headers. Only the SSE endpoint accepts access_token.
        if ("/api/v1/realtime/road-risk/stream".equals(request.getRequestURI())) {
            String queryToken = request.getParameter("access_token");
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken.trim();
            }
        }

        throw new UnauthorizedException("Missing Authorization bearer token");
    }

    private void writeError(
            HttpServletResponse response,
            HttpServletRequest request,
            HttpStatus status,
            String message
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                new ErrorResponse(
                        LocalDateTime.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        request.getRequestURI()
                )
        );
    }
}
