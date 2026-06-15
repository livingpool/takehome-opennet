package com.example.demo.mq;

import com.example.demo.dto.RateLimitEventMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "rocketmq", name = "enabled", havingValue = "false",
        matchIfMissing = true)
public class NoopRateLimitEventPublisher implements RateLimitEventPublisher {
    @Override
    public void publish(RateLimitEventMessage event) {
    }
}
