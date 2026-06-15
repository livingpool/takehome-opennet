package com.example.demo.service;

import com.example.demo.config.RateLimitRedisProperties;
import com.example.demo.model.CachedRateLimitConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitRedisService {
    private static final RedisScript<List> INCREMENT_USAGE_SCRIPT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('TTL', KEYS[1])
            return {count, ttl}
            """, List.class);
    private static final RedisScript<List> GET_USAGE_SCRIPT = RedisScript.of("""
            local usage = redis.call('GET', KEYS[1])
            if not usage then
                return {0, 0}
            end
            
            local ttl = redis.call('TTL', KEYS[1])
            if ttl < 0 then
                ttl = 0
            end
            
            return {tonumber(usage), ttl}
            """, List.class);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimitRedisProperties properties;

    public RateLimitRedisService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                 RateLimitRedisProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<CachedRateLimitConfig> getConfig(String apiKey) {
        String value = redisTemplate.opsForValue().get(configKey(apiKey));
        if (value == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(value, CachedRateLimitConfig.class));
        } catch (JsonProcessingException ex) {
            redisTemplate.delete(configKey(apiKey));
            return Optional.empty();
        }
    }

    public void putConfig(CachedRateLimitConfig config) {
        try {
            String value = objectMapper.writeValueAsString(config);
            redisTemplate.opsForValue().set(configKey(config.apiKey()), value,
                    properties.configTtl());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize rate limit config", ex);
        }
    }

    public void evictConfig(String apiKey) {
        redisTemplate.delete(configKey(apiKey));
    }

    public UsageIncrementResult incrementUsage(String apiKey, int windowSeconds) {
        List<?> result = redisTemplate.execute(INCREMENT_USAGE_SCRIPT, List.of(usageKey(apiKey)),
                String.valueOf(windowSeconds));

        if (result.size() < 2) {
            return new UsageIncrementResult(0, 0);
        }

        long usage = toLong(result.get(0));
        long ttlSeconds = Math.max(0, toLong(result.get(1)));
        return new UsageIncrementResult(usage, ttlSeconds);
    }

    public long getTtlSeconds(String apiKey) {
        Long ttlSeconds = redisTemplate.getExpire(usageKey(apiKey), TimeUnit.SECONDS);
        return ttlSeconds < 0 ? 0 : ttlSeconds;
    }

    public long getUsage(String apiKey) {
        String value = redisTemplate.opsForValue().get(usageKey(apiKey));
        return value == null ? 0 : Long.parseLong(value);
    }

    public UsageSnapshot getUsageSnapshot(String apiKey) {
        List<?> result = redisTemplate.execute(GET_USAGE_SCRIPT, List.of(usageKey(apiKey)));

        if (result.size() < 2) {
            return new UsageSnapshot(0, 0);
        }

        long usage = toLong(result.get(0));
        long ttlSeconds = Math.max(0, toLong(result.get(1)));
        return new UsageSnapshot(usage, ttlSeconds);
    }

    public void clearUsage(String apiKey) {
        redisTemplate.delete(usageKey(apiKey));
    }

    private String configKey(String apiKey) {
        return properties.configKeyPrefix() + apiKey;
    }

    private String usageKey(String apiKey) {
        return properties.usageKeyPrefix() + apiKey;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.parseLong(value.toString());
    }

    public record UsageIncrementResult(long usage, long ttlSeconds) {
    }

    public record UsageSnapshot(long usage, long ttlSeconds) {
    }
}
