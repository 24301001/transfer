package com.transfer.config;

import com.transfer.service.OperationLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiAccessLogFilter extends OncePerRequestFilter {

    private final OperationLogService operationLogService;

    public ApiAccessLogFilter(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (shouldRecord(request)) {
                recordSafely(request, response, System.currentTimeMillis() - startedAt);
            }
        }
    }

    private boolean shouldRecord(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        return uri.startsWith("/api/")
                && !uri.startsWith("/api/v1/realtime")
                && !uri.startsWith("/api/health");
    }

    private void recordSafely(HttpServletRequest request, HttpServletResponse response, long costMs) {
        try {
            operationLogService.record(
                    null,
                    "API_CALL",
                    "HttpRequest",
                    request.getMethod() + " " + request.getRequestURI(),
                    clientIp(request),
                    "status=" + response.getStatus() + ", costMs=" + costMs
            );
        } catch (Exception ignored) {
            // 运行监控日志不能影响业务接口响应。
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
