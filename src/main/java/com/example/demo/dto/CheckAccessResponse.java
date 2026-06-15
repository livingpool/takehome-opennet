package com.example.demo.dto;

public record CheckAccessResponse(
        long usage,
        long remaining,
        long ttlSeconds) {
}
