package com.example.demo.dto;

import java.time.Instant;

public record RateLimitExceededResponse(
        Instant timestamp,
        String message) {
}
