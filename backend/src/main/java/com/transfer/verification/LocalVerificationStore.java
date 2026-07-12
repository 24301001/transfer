package com.transfer.verification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
/**
 * 本地内存验证存储 — 开发环境 Redis 不可用时的降级方案。
 * {@code @Primary} 优先于 {@code RedisVerificationStore} 注入。
 */
@Component
@Primary
public class LocalVerificationStore implements VerificationStore {

    private static final Logger log = LoggerFactory.getLogger(LocalVerificationStore.class);

    private final Map<String, ValueWithExpiry> store = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String keyPrefix = "transfer:local:";

    public LocalVerificationStore() {
        // 每分钟清理一次过期条目
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "local-verification-cleaner");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::evictExpired, 1, 1, TimeUnit.MINUTES);
        log.info("LocalVerificationStore initialized (in-memory fallback, no Redis required)");
    }

    @Override
    public String key(String suffix) {
        return keyPrefix + suffix;
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        long expiresAt = System.currentTimeMillis() + ttl.toMillis();
        return store.putIfAbsent(key, new ValueWithExpiry(value, expiresAt)) == null;
    }

    @Override
    public void setString(String key, String value, Duration ttl) {
        long expiresAt = System.currentTimeMillis() + ttl.toMillis();
        store.put(key, new ValueWithExpiry(value, expiresAt));
    }

    @Override
    public String getString(String key) {
        ValueWithExpiry entry = store.get(key);
        if (entry == null || entry.isExpired()) {
            if (entry != null) store.remove(key);
            return null;
        }
        return entry.value();
    }

    @Override
    public String consumeString(String key) {
        ValueWithExpiry entry = store.remove(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.value();
    }

    @Override
    public long consumeIfEquals(String key, String expectedValue) {
        ValueWithExpiry entry = store.get(key);
        if (entry == null || entry.isExpired()) {
            if (entry != null) store.remove(key);
            return 0L;
        }
        if (!entry.value().equals(expectedValue)) {
            return -1L;
        }
        store.remove(key);
        return 1L;
    }

    @Override
    public void setJson(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            setString(key, json, ttl);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize value to JSON", ex);
        }
    }

    @Override
    public <T> T getJson(String key, Class<T> valueType) {
        String value = getString(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, valueType);
        } catch (Exception ex) {
            store.remove(key);
            return null;
        }
    }

    @Override
    public void updateJsonPreservingTtl(String key, Object value) {
        Duration ttl = getTtl(key);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            store.remove(key);
            return;
        }
        setJson(key, value, ttl);
    }

    @Override
    public Duration getTtl(String key) {
        ValueWithExpiry entry = store.get(key);
        if (entry == null || entry.isExpired()) {
            if (entry != null) store.remove(key);
            return null;
        }
        long remaining = entry.expiresAt() - System.currentTimeMillis();
        return remaining > 0 ? Duration.ofMillis(remaining) : null;
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    private void evictExpired() {
        store.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private record ValueWithExpiry(String value, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }
}
