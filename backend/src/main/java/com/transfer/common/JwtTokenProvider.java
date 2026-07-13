package com.transfer.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transfer.model.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final String secret;
    private final Long expirationSeconds;
    private final Long clockSkewSeconds;
    private final String issuer;
    private final String audience;

    public JwtTokenProvider(
            ObjectMapper objectMapper,
            @Value("${app.auth.jwt-secret:}") String secret,
            @Value("${app.auth.jwt-expiration-seconds:86400}") Long expirationSeconds,
            @Value("${app.auth.jwt-clock-skew-seconds:30}") Long clockSkewSeconds,
            @Value("${app.auth.jwt-issuer:traffic-risk-backend}") String issuer,
            @Value("${app.auth.jwt-audience:traffic-risk-web}") String audience
    ) {
        this.objectMapper = objectMapper;
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "APP_AUTH_JWT_SECRET must be configured with at least 32 UTF-8 bytes"
            );
        }
        if (expirationSeconds == null || expirationSeconds <= 0) {
            throw new IllegalStateException("JWT expiration must be greater than zero");
        }
        if (clockSkewSeconds == null || clockSkewSeconds < 0) {
            throw new IllegalStateException("JWT clock skew must not be negative");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalStateException("JWT issuer and audience must be configured");
        }
        this.secret = secret;
        this.expirationSeconds = expirationSeconds;
        this.clockSkewSeconds = clockSkewSeconds;
        this.issuer = issuer;
        this.audience = audience;
    }

    public IssuedToken issueToken(UserAccount user) {
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + expirationSeconds;
        String tokenId = UUID.randomUUID().toString();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("aud", audience);
        payload.put("jti", tokenId);
        payload.put("sub", user.getId());
        payload.put("username", user.getUsername());
        payload.put("role", user.getRole().name());
        payload.put("iat", now);
        payload.put("exp", expiresAt);

        String unsignedToken = base64UrlEncode(writeJson(header)) + "." + base64UrlEncode(writeJson(payload));
        String token = unsignedToken + "." + sign(unsignedToken);

        return new IssuedToken(
                token,
                new JwtClaims(
                        user.getId(),
                        user.getUsername(),
                        user.getRole().name(),
                        tokenId,
                        now,
                        expiresAt,
                        issuer,
                        audience
                )
        );
    }

    public String createToken(UserAccount user) {
        return issueToken(user).token();
    }

    public Optional<String> resolveToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return Optional.empty();
        }

        String value = authorizationHeader.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = value.substring(7).trim();
            return token.isEmpty() ? Optional.empty() : Optional.of(token);
        }
        return Optional.empty();
    }

    public JwtClaims parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Missing token");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new UnauthorizedException("Invalid token format");
        }

        Map<String, Object> header = readJson(base64UrlDecode(parts[0]));
        if (!"HS256".equals(toStringOrNull(header.get("alg")))
                || !"JWT".equalsIgnoreCase(toStringOrNull(header.get("typ")))) {
            throw new UnauthorizedException("Unsupported token header");
        }

        String unsignedToken = parts[0] + "." + parts[1];
        String expectedSignature = sign(unsignedToken);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII)
        )) {
            throw new UnauthorizedException("Invalid token signature");
        }

        Map<String, Object> payload = readJson(base64UrlDecode(parts[1]));
        Long userId = toLong(payload.get("sub"));
        Long issuedAt = toLong(payload.get("iat"));
        Long expiresAt = toLong(payload.get("exp"));
        String tokenId = toStringOrNull(payload.get("jti"));
        String tokenIssuer = toStringOrNull(payload.get("iss"));
        String tokenAudience = toStringOrNull(payload.get("aud"));
        String username = toStringOrNull(payload.get("username"));
        String role = toStringOrNull(payload.get("role"));

        if (userId == null || issuedAt == null || expiresAt == null
                || tokenId == null || tokenId.isBlank()
                || username == null || username.isBlank()
                || role == null || role.isBlank()) {
            throw new UnauthorizedException("Invalid token payload");
        }
        if (!issuer.equals(tokenIssuer) || !audience.equals(tokenAudience)) {
            throw new UnauthorizedException("Invalid token issuer or audience");
        }

        long now = Instant.now().getEpochSecond();
        if (issuedAt > now + clockSkewSeconds) {
            throw new UnauthorizedException("Token issued-at time is invalid");
        }
        if (now > expiresAt + clockSkewSeconds) {
            throw new UnauthorizedException("Token has expired");
        }
        if (expiresAt <= issuedAt) {
            throw new UnauthorizedException("Token lifetime is invalid");
        }

        return new JwtClaims(
                userId,
                username,
                role,
                tokenId,
                issuedAt,
                expiresAt,
                tokenIssuer,
                tokenAudience
        );
    }

    public Long getExpirationSeconds() {
        return expirationSeconds;
    }

    private String writeJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write JWT JSON", ex);
        }
    }

    private Map<String, Object> readJson(byte[] data) {
        try {
            return objectMapper.readValue(data, MAP_TYPE);
        } catch (Exception ex) {
            throw new UnauthorizedException("Invalid token payload");
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] signature = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign token", ex);
        }
    }

    private String base64UrlEncode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] base64UrlDecode(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid token encoding");
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String toStringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    public record IssuedToken(
            String token,
            JwtClaims claims
    ) {
    }

    public record JwtClaims(
            Long userId,
            String username,
            String role,
            String tokenId,
            Long issuedAt,
            Long expiresAt,
            String issuer,
            String audience
    ) {
    }
}
