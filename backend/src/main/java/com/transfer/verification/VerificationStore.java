package com.transfer.verification;

import java.time.Duration;

/**
 * 验证数据存储接口 — 支持 Redis 和本地内存两种实现。
 */
public interface VerificationStore {

    String key(String suffix);

    boolean setIfAbsent(String key, String value, Duration ttl);

    void setString(String key, String value, Duration ttl);

    String getString(String key);

    String consumeString(String key);

    long consumeIfEquals(String key, String expectedValue);

    void setJson(String key, Object value, Duration ttl);

    <T> T getJson(String key, Class<T> valueType);

    void updateJsonPreservingTtl(String key, Object value);

    Duration getTtl(String key);

    void delete(String key);
}
