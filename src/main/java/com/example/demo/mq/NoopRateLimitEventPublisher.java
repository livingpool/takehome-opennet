package com.example.demo.mq;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.demo.dto.RateLimitEventMessage;

@Component
@ConditionalOnProperty(prefix = "rocketmq", name = "enabled", havingValue = "false",
        matchIfMissing = true)
public class NoopRateLimitEventPublisher implements RateLimitEventPublisher {
    @Override
    public void publish(RateLimitEventMessage event) {}
}
