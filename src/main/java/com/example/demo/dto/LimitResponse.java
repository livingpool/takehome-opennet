package com.example.demo.dto;

public record LimitResponse(
        String apiKey,
        int limit,
        int windowSeconds) {
}
