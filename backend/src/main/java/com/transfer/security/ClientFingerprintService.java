package com.transfer.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class ClientFingerprintService {

    private final boolean bindClient;

    public ClientFingerprintService(
            @Value("${app.verification.slider-bind-client:true}") boolean bindClient
    ) {
        this.bindClient = bindClient;
    }

    public String fingerprint(HttpServletRequest request) {
        if (!bindClient) {
            return "client-binding-disabled";
        }
        String raw = clientIp(request)
                + "|" + header(request, "User-Agent")
                + "|" + header(request, "Accept-Language");
        return sha256(raw);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? "" : value.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
