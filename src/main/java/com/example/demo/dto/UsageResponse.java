package com.example.demo.dto;

public record UsageResponse(
        long usage,
        long remaining,
        long ttlSeconds) {
}
