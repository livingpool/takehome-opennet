package com.example.demo.mq;

import com.example.demo.dto.RateLimitEventMessage;

public interface RateLimitEventPublisher {
    void publish(RateLimitEventMessage event);
}
