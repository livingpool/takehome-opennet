package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "rate-limit.redis")
public record RateLimitRedisProperties(
        String configKeyPrefix,
        String usageKeyPrefix,
        Duration configTtl) {
    public RateLimitRedisProperties {
        if (configKeyPrefix == null || configKeyPrefix.isBlank()) {
            configKeyPrefix = "rate_limit:config:";
        }
        if (usageKeyPrefix == null || usageKeyPrefix.isBlank()) {
            usageKeyPrefix = "rate_limit:usage:";
        }
        if (configTtl == null) {
            configTtl = Duration.ofMinutes(10);
        }
    }
}
