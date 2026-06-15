package com.example.demo.dto;

import java.time.Instant;

public record RateLimitEventMessage(
                String eventType,
                String apiKey,
                boolean allowed,
                long usage,
                int limit,
                int windowSeconds,
                long remaining,
                Instant timestamp) {
}
