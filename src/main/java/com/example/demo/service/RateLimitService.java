package com.example.demo.service;

import java.util.List;
import java.time.Instant;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.CheckAccessResponse;
import com.example.demo.dto.CreateLimitRequest;
import com.example.demo.dto.LimitResponse;
import com.example.demo.dto.PagedLimitResponse;
import com.example.demo.dto.UsageResponse;
import com.example.demo.config.PagingProperties;
import com.example.demo.exception.RateLimitExceededException;
import com.example.demo.exception.RateLimitNotFoundException;
import com.example.demo.model.CachedRateLimitConfig;
import com.example.demo.model.RateLimit;
import com.example.demo.dto.RateLimitEventMessage;
import com.example.demo.mq.RateLimitEventPublisher;
import com.example.demo.repository.RateLimitRepository;
import com.example.demo.service.RateLimitRedisService.UsageIncrementResult;
import com.example.demo.service.RateLimitRedisService.UsageSnapshot;

@Service
public class RateLimitService {
    private static final String EVENT_TYPE_ALLOWED = "RATE_LIMIT_ALLOWED";
    private static final String EVENT_TYPE_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    private static final String EVENT_TYPE_RULE_UPSERTED = "RATE_LIMIT_RULE_UPSERTED";
    private static final String EVENT_TYPE_RULE_DELETED = "RATE_LIMIT_RULE_DELETED";

    private final RateLimitRepository rateLimitRepository;
    private final RateLimitRedisService rateLimitRedisService;
    private final PagingProperties pagingProperties;
    private final RateLimitEventPublisher eventPublisher;

    public RateLimitService(RateLimitRepository rateLimitRepository,
            RateLimitRedisService rateLimitRedisService, PagingProperties pagingProperties,
            RateLimitEventPublisher eventPublisher) {
        this.rateLimitRepository = rateLimitRepository;
        this.rateLimitRedisService = rateLimitRedisService;
        this.pagingProperties = pagingProperties;
        this.eventPublisher = eventPublisher;
    }

    // cache aside: writes in mysql and deletes all related entries in redis
    @Transactional
    public void createOrUpdateLimit(CreateLimitRequest request) {
        rateLimitRepository.upsert(request.apiKey(), request.limit(), request.windowSeconds());
        rateLimitRedisService.evictConfig(request.apiKey());
        rateLimitRedisService.clearUsage(request.apiKey());
        publishRuleEvent(EVENT_TYPE_RULE_UPSERTED, request.apiKey(), request.limit(),
                request.windowSeconds());
    }

    // cache aside: on cache hit, read from redis;
    // on cache miss, read from mysql and put in redis
    public CheckAccessResponse checkAccess(String apiKey) {
        CachedRateLimitConfig config = getConfig(apiKey);
        UsageIncrementResult usageResult =
                rateLimitRedisService.incrementUsage(apiKey, config.windowSeconds());
        long usage = usageResult.usage();
        long remaining = Math.max(0, (long) config.limit() - usage);

        if (usage > config.limit()) {
            publishCheckEvent(apiKey, config, usage, remaining, false);
            throw new RateLimitExceededException(apiKey);
        }

        publishCheckEvent(apiKey, config, usage, remaining, true);

        return new CheckAccessResponse(usage, remaining, usageResult.ttlSeconds());
    }

    // read directly from redis
    public UsageResponse getUsage(String apiKey) {
        CachedRateLimitConfig config = getConfig(apiKey);
        UsageSnapshot snapshot = rateLimitRedisService.getUsageSnapshot(apiKey);
        long remaining = Math.max(0, (long) config.limit() - snapshot.usage());

        return new UsageResponse(snapshot.usage(), remaining, snapshot.ttlSeconds());
    }

    // delete config in mysql and all related entries in redis
    @Transactional
    public void deleteLimit(String apiKey) {
        RateLimit existing = rateLimitRepository.findByApiKey(apiKey).orElse(null);
        long deleted = rateLimitRepository.deleteByApiKey(apiKey);
        rateLimitRedisService.evictConfig(apiKey);
        rateLimitRedisService.clearUsage(apiKey);

        if (deleted > 0 && existing != null) {
            publishRuleEvent(EVENT_TYPE_RULE_DELETED, existing.apiKey(), existing.requestLimit(),
                    existing.windowSeconds());
        }
    }

    // read all entries in mysql with pagination
    public PagedLimitResponse getLimits(int page, int size) {
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.min(pagingProperties.maxSize(), Math.max(1, size));
        Pageable pageable =
                PageRequest.of(normalizedPage, normalizedSize, Sort.by("id").ascending());
        List<LimitResponse> items =
                rateLimitRepository.findAll(pageable).stream().map(this::toLimitResponse).toList();
        long totalItems = rateLimitRepository.count();
        int totalPages = (int) Math.ceil((double) totalItems / normalizedSize);

        return new PagedLimitResponse(items, normalizedPage, normalizedSize, totalItems,
                totalPages);
    }

    private CachedRateLimitConfig getConfig(String apiKey) {
        return rateLimitRedisService.getConfig(apiKey).orElseGet(() -> loadAndCacheConfig(apiKey));
    }

    private CachedRateLimitConfig loadAndCacheConfig(String apiKey) {
        RateLimit rateLimit = rateLimitRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new RateLimitNotFoundException(apiKey));

        CachedRateLimitConfig config = new CachedRateLimitConfig(rateLimit.apiKey(),
                rateLimit.requestLimit(), rateLimit.windowSeconds());
        rateLimitRedisService.putConfig(config);

        return config;
    }

    private LimitResponse toLimitResponse(RateLimit rateLimit) {
        return new LimitResponse(rateLimit.apiKey(), rateLimit.requestLimit(),
                rateLimit.windowSeconds());
    }

    private void publishCheckEvent(String apiKey, CachedRateLimitConfig config, long usage,
            long remaining, boolean allowed) {
        eventPublisher.publish(new RateLimitEventMessage(
                allowed ? EVENT_TYPE_ALLOWED : EVENT_TYPE_EXCEEDED, apiKey, allowed, usage,
                config.limit(), config.windowSeconds(), remaining, Instant.now()));
    }

    private void publishRuleEvent(String eventType, String apiKey, int limit, int windowSeconds) {
        eventPublisher.publish(new RateLimitEventMessage(eventType, apiKey, true, 0, limit,
                windowSeconds, 0, Instant.now()));
    }
}
