package com.transfer.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transfer.common.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class RedisVerificationStore implements VerificationStore {

    private static final DefaultRedisScript<String> GET_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); "
                    + "if value then redis.call('DEL', KEYS[1]); end; "
                    + "return value;",
            String.class
    );

    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); "
                    + "if not value then return 0; end; "
                    + "if value == ARGV[1] then redis.call('DEL', KEYS[1]); return 1; end; "
                    + "return -1;",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;

    public RedisVerificationStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.redis.key-prefix:transfer}") String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = normalizePrefix(keyPrefix);
    }

    public String key(String suffix) {
        return keyPrefix + ":" + suffix;
    }

    public boolean setIfAbsent(String key, String value, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
        } catch (DataAccessException ex) {
            throw redisUnavailable(ex);
        }
    }

    public void setString(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (DataAccessException ex) {
            throw redisUnavailable(ex);
        }
    }

    public String getString(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (DataAccessException ex) {
            throw redisUnavailable(ex);
        }
    }

    public String consumeString(String key) {
        try {
            return redisTemplate.execute(GET_AND_DELETE_SCRIPT, List.of(key));
        } catch (DataAccessException ex) {
            throw redisUnavailable(ex);
        }
    }


    /**
     * 原子比较并消费一次性凭证。
     *
     * @return 1=匹配且已删除，0=不存在/已过期，-1=存在但不匹配
     */
    public long consumeIfEquals(String key, String expectedValue) {
        try {
            Long result = redisTemplate.execute(
                    COMPARE_AND_DELETE_SCRIPT,
                    List.of(key),
                    expectedValue
            );
            return result == null ? 0L : result;
        } catch (DataAccessException ex) {
            throw redisUnavailable(ex);
        }
    }

    public void setJson(String key, Object value, Duration ttl) {
        try {
            setString(key, objectMapper.writeValueAsString(value), ttl);
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize Redis verification state", ex);
        }
    }

    public <T> T getJson(String key, Class<T> valueType) {
        String value = getString(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, valueType);
        } catch (Exception ex) {
            delete(key);
            return null;
        }
    }

    public void updateJsonPreservingTtl(String key, Object value) {
        Duration ttl = getTtl(key);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            delete(key);
            return;
        }
        setJson(key, value, ttl);
    }

    public Duration getTtl(String key) {
        try {
            Long seconds = redisTemplate.getExpire(key);
            if (seconds == null || seconds <= 0) {
                return null;
            }
            return Duration.ofSeconds(seconds);
        } catch (DataAccessException ex) {
            throw redisUnavailable(ex);
        }
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException ex) {
            throw redisUnavailable(ex);
        }
    }

    private ServiceUnavailableException redisUnavailable(Exception ex) {
        return new ServiceUnavailableException("Redis 验证服务暂不可用，请稍后重试", ex);
    }

    private String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "transfer";
        }
        return value.trim().replaceAll(":+$", "");
    }
}
