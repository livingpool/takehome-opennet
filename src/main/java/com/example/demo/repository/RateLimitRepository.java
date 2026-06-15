package com.example.demo.repository;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.model.RateLimit;

@Repository
public interface RateLimitRepository
		extends ListCrudRepository<RateLimit, Long>, PagingAndSortingRepository<RateLimit, Long> {
	Optional<RateLimit> findByApiKey(String apiKey);

	long deleteByApiKey(String apiKey);

	@Modifying
	@Query("""
			INSERT INTO rate_limit (api_key, request_limit, window_seconds)
			VALUES (:apiKey, :requestLimit, :windowSeconds)
			ON DUPLICATE KEY UPDATE
				request_limit = VALUES(request_limit),
				window_seconds = VALUES(window_seconds),
				updated_at = CURRENT_TIMESTAMP
			""")
	int upsert(@Param("apiKey") String apiKey,
			@Param("requestLimit") int requestLimit,
			@Param("windowSeconds") int windowSeconds);
}
