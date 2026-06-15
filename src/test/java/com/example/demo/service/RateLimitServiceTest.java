package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.config.PagingProperties;
import com.example.demo.dto.CheckAccessResponse;
import com.example.demo.dto.CreateLimitRequest;
import com.example.demo.dto.RateLimitEventMessage;
import com.example.demo.exception.RateLimitExceededException;
import com.example.demo.model.CachedRateLimitConfig;
import com.example.demo.model.RateLimit;
import com.example.demo.mq.RateLimitEventPublisher;
import com.example.demo.repository.RateLimitRepository;
import com.example.demo.service.RateLimitRedisService.UsageIncrementResult;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {
        @Mock
        private RateLimitRepository rateLimitRepository;

        @Mock
        private RateLimitRedisService rateLimitRedisService;

        @Mock
        private RateLimitEventPublisher eventPublisher;

        private RateLimitService rateLimitService;

        private final ArgumentCaptor<RateLimitEventMessage> eventCaptor =
                        ArgumentCaptor.forClass(RateLimitEventMessage.class);

        @BeforeEach
        void setUp() {
                rateLimitService = new RateLimitService(rateLimitRepository, rateLimitRedisService,
                                new PagingProperties(0, 20, 100), eventPublisher);
        }

        @Test
        void checkAccessPublishesAllowedEvent() {
                when(rateLimitRedisService.getConfig("abc-123")).thenReturn(
                                Optional.of(new CachedRateLimitConfig("abc-123", 3, 60)));
                when(rateLimitRedisService.incrementUsage("abc-123", 60))
                                .thenReturn(new UsageIncrementResult(2, 50));

                CheckAccessResponse response = rateLimitService.checkAccess("abc-123");

                assertThat(response.usage()).isEqualTo(2);
                assertThat(response.remaining()).isEqualTo(1);
                assertThat(response.ttlSeconds()).isEqualTo(50);

                verify(eventPublisher).publish(eventCaptor.capture());
                RateLimitEventMessage event = eventCaptor.getValue();
                assertThat(event.eventType()).isEqualTo("RATE_LIMIT_ALLOWED");
                assertThat(event.apiKey()).isEqualTo("abc-123");
                assertThat(event.allowed()).isTrue();
                assertThat(event.usage()).isEqualTo(2);
                assertThat(event.limit()).isEqualTo(3);
                assertThat(event.windowSeconds()).isEqualTo(60);
                assertThat(event.remaining()).isEqualTo(1);
                assertThat(event.timestamp()).isNotNull();
        }

        @Test
        void checkAccessPublishesExceededEventBeforeThrowing() {
                when(rateLimitRedisService.getConfig("abc-123")).thenReturn(
                                Optional.of(new CachedRateLimitConfig("abc-123", 3, 60)));
                when(rateLimitRedisService.incrementUsage("abc-123", 60))
                                .thenReturn(new UsageIncrementResult(4, 50));

                assertThatThrownBy(() -> rateLimitService.checkAccess("abc-123"))
                                .isInstanceOf(RateLimitExceededException.class);

                verify(eventPublisher).publish(eventCaptor.capture());
                RateLimitEventMessage event = eventCaptor.getValue();
                assertThat(event.eventType()).isEqualTo("RATE_LIMIT_EXCEEDED");
                assertThat(event.apiKey()).isEqualTo("abc-123");
                assertThat(event.allowed()).isFalse();
                assertThat(event.usage()).isEqualTo(4);
                assertThat(event.limit()).isEqualTo(3);
                assertThat(event.windowSeconds()).isEqualTo(60);
                assertThat(event.remaining()).isEqualTo(0);
                assertThat(event.timestamp()).isNotNull();
        }

        @Test
        void createOrUpdateLimitPublishesRuleUpsertedEvent() {
                CreateLimitRequest request = new CreateLimitRequest("abc-123", 100, 60);

                rateLimitService.createOrUpdateLimit(request);

                verify(rateLimitRepository).upsert("abc-123", 100, 60);
                verify(rateLimitRedisService).evictConfig("abc-123");
                verify(rateLimitRedisService).clearUsage("abc-123");
                verify(eventPublisher).publish(eventCaptor.capture());

                RateLimitEventMessage event = eventCaptor.getValue();
                assertThat(event.eventType()).isEqualTo("RATE_LIMIT_RULE_UPSERTED");
                assertThat(event.apiKey()).isEqualTo("abc-123");
                assertThat(event.allowed()).isTrue();
                assertThat(event.usage()).isZero();
                assertThat(event.limit()).isEqualTo(100);
                assertThat(event.windowSeconds()).isEqualTo(60);
                assertThat(event.remaining()).isZero();
                assertThat(event.timestamp()).isNotNull();
        }

        @Test
        void deleteLimitPublishesRuleDeletedEventWhenRuleExists() {
                RateLimit existing =
                                new RateLimit(1L, "abc-123", 100, 60, Instant.now(), Instant.now());
                when(rateLimitRepository.findByApiKey("abc-123")).thenReturn(Optional.of(existing));
                when(rateLimitRepository.deleteByApiKey("abc-123")).thenReturn(1L);

                rateLimitService.deleteLimit("abc-123");

                verify(rateLimitRepository).deleteByApiKey("abc-123");
                verify(rateLimitRedisService).evictConfig("abc-123");
                verify(rateLimitRedisService).clearUsage("abc-123");
                verify(eventPublisher).publish(eventCaptor.capture());

                RateLimitEventMessage event = eventCaptor.getValue();
                assertThat(event.eventType()).isEqualTo("RATE_LIMIT_RULE_DELETED");
                assertThat(event.apiKey()).isEqualTo("abc-123");
                assertThat(event.allowed()).isTrue();
                assertThat(event.usage()).isZero();
                assertThat(event.limit()).isEqualTo(100);
                assertThat(event.windowSeconds()).isEqualTo(60);
                assertThat(event.remaining()).isZero();
                assertThat(event.timestamp()).isNotNull();
        }
}
