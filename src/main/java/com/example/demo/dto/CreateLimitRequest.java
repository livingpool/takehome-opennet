package com.example.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateLimitRequest(
        @NotBlank String apiKey,
        @Min(1) int limit,
        @Min(1) int windowSeconds) {
}
