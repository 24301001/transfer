package com.transfer.security;

import com.transfer.common.JwtTokenProvider;
import com.transfer.common.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 本地内存 Token 会话服务 — 开发环境 Redis 不可用时降级使用。
 */
@Service
@Primary
public class LocalTokenSessionService implements TokenSessionService {

    private static final Logger log = LoggerFactory.getLogger(LocalTokenSessionService.class);

    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> userTokens = new ConcurrentHashMap<>();

    public LocalTokenSessionService() {
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "local-token-cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::evictExpired, 1, 1, TimeUnit.MINUTES);
        log.info("LocalTokenSessionService initialized (in-memory fallback, no Redis required)");
    }

    @Override
    public void store(JwtTokenProvider.JwtClaims claims) {
        long ttlSeconds = claims.expiresAt() - Instant.now().getEpochSecond();
        if (ttlSeconds <= 0) {
            throw new UnauthorizedException("Token has expired");
        }
        long now = Instant.now().toEpochMilli();
        sessions.put(claims.tokenId(), new SessionEntry(claims, now + ttlSeconds * 1000));
        userTokens.computeIfAbsent(claims.userId(), k -> ConcurrentHashMap.newKeySet()).add(claims.tokenId());
    }

    @Override
    public void validate(JwtTokenProvider.JwtClaims claims) {
        SessionEntry entry = sessions.get(claims.tokenId());
        if (entry == null || entry.isExpired()) {
            if (entry != null) sessions.remove(claims.tokenId());
            throw new UnauthorizedException("Token session is missing or has been revoked");
        }
        JwtTokenProvider.JwtClaims stored = entry.claims();
        if (!claims.userId().equals(stored.userId())
                || !safeEquals(claims.username(), stored.username())
                || !safeEquals(claims.role(), stored.role())
                || !claims.expiresAt().equals(stored.expiresAt())) {
            revoke(claims.tokenId());
            throw new UnauthorizedException("Token session does not match token claims");
        }
    }

    @Override
    public void revoke(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) return;
        SessionEntry entry = sessions.remove(tokenId);
        if (entry != null) {
            Set<String> tokens = userTokens.get(entry.claims().userId());
            if (tokens != null) tokens.remove(tokenId);
        }
    }

    @Override
    public void revokeAll(Long userId) {
        if (userId == null) return;
        Set<String> tokenIds = userTokens.remove(userId);
        if (tokenIds != null) {
            tokenIds.forEach(sessions::remove);
        }
    }

    private void evictExpired() {
        sessions.entrySet().removeIf(e -> e.getValue().isExpired());
        userTokens.values().forEach(tokens -> tokens.removeIf(id -> {
            SessionEntry e = sessions.get(id);
            return e == null || e.isExpired();
        }));
        userTokens.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private record SessionEntry(JwtTokenProvider.JwtClaims claims, long expiresAt) {
        boolean isExpired() { return System.currentTimeMillis() >= expiresAt; }
    }
}
