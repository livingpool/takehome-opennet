package com.example.demo.dto;

import java.util.List;

public record PagedLimitResponse(
        List<LimitResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
