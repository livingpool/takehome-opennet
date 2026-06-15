package com.example.demo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("rate_limit")
public record RateLimit(
        @Id Long id,
        @Column("api_key") String apiKey,
        @Column("request_limit") int requestLimit,
        @Column("window_seconds") int windowSeconds,
        @ReadOnlyProperty @Column("created_at") Instant createdAt,
        @ReadOnlyProperty @Column("updated_at") Instant updatedAt) {
}
