package com.example.demo.model;

public record CachedRateLimitConfig(
                String apiKey,
                int limit,
                int windowSeconds) {
}
