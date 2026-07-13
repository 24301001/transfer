package com.transfer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transfer.common.JwtTokenProvider;
import com.transfer.common.ServiceUnavailableException;
import com.transfer.common.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

@Service
public class RedisTokenSessionService implements TokenSessionService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;

    public RedisTokenSessionService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.redis.key-prefix:transfer}") String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = normalizePrefix(keyPrefix);
    }

    public void store(JwtTokenProvider.JwtClaims claims) {
        long ttlSeconds = claims.expiresAt() - Instant.now().getEpochSecond();
        if (ttlSeconds <= 0) {
            throw new UnauthorizedException("Token has expired");
        }

        TokenSession session = new TokenSession(
                claims.userId(),
                claims.username(),
                claims.role(),
                claims.issuedAt(),
                claims.expiresAt()
        );

        try {
            String json = objectMapper.writeValueAsString(session);
            Duration ttl = Duration.ofSeconds(ttlSeconds);
            redisTemplate.opsForValue().set(sessionKey(claims.tokenId()), json, ttl);
            redisTemplate.opsForSet().add(userSessionsKey(claims.userId()), claims.tokenId());
            redisTemplate.expire(userSessionsKey(claims.userId()), ttl);
        } catch (DataAccessException ex) {
            throw redisUnavailable(ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize token session", ex);
        }
    }

    public void validate(JwtTokenProvider.JwtClaims claims) {
        TokenSession session = find(claims.tokenId());
        if (session == null) {
            throw new UnauthorizedException("Token session is missing or has been revoked");
        }
        if (!claims.userId().equals(session.userId())
                || !safeEquals(claims.username(), session.username())
                || !safeEquals(claims.role(), session.role())
                || !claims.expiresAt().equals(session.expiresAt())) {
            revoke(claims.tokenId());
            throw new UnauthorizedException("Token session does not match token claims");
        }
    }

    public void revoke(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return;
        }
        try {
            TokenSession session = find(tokenId);
            redisTemplate.delete(sessionKey(tokenId));
            if (session != null) {
                redisTemplate.opsForSet().remove(userSessionsKey(session.userId()), tokenId);
            }
        } catch (DataAccessException ex) {
            throw redisUnavailable(ex);
        }
    }

    public void revokeAll(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            String indexKey = userSessionsKey(userId);
            Set<String> tokenIds = redisTemplate.opsForSet().members(indexKey);
            if (tokenIds != null && !tokenIds.isEmpty()) {
                Collection<String> keys = new ArrayList<>(tokenIds.size());
                for (String tokenId : tokenIds) {
                    keys.add(sessionKey(tokenId));
                }
                redisTemplate.delete(keys);
            }
            redisTemplate.delete(indexKey);
        } catch (DataAccessException ex) {
            throw redisUnavailable(ex);
        }
    }

    private TokenSession find(String tokenId) {
        try {
            String json = redisTemplate.opsForValue().get(sessionKey(tokenId));
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, TokenSession.class);
        } catch (DataAccessException ex) {
            throw redisUnavailable(ex);
        } catch (Exception ex) {
            revokeCorruptedSession(tokenId);
            return null;
        }
    }

    private void revokeCorruptedSession(String tokenId) {
        try {
            redisTemplate.delete(sessionKey(tokenId));
        } catch (Exception ignored) {
            // Corrupted session cleanup must not mask the authentication result.
        }
    }

    private ServiceUnavailableException redisUnavailable(Exception ex) {
        return new ServiceUnavailableException("认证会话服务暂不可用，请稍后重试", ex);
    }

    private String sessionKey(String tokenId) {
        return keyPrefix + ":auth:session:" + tokenId;
    }

    private String userSessionsKey(Long userId) {
        return keyPrefix + ":auth:user-sessions:" + userId;
    }

    private String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "transfer";
        }
        return value.trim().replaceAll(":+$", "");
    }

    private boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    public record TokenSession(
            Long userId,
            String username,
            String role,
            Long issuedAt,
            Long expiresAt
    ) {
    }
}
