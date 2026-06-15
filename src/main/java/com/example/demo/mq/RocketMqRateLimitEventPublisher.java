package com.example.demo.mq;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.demo.config.RocketMqProperties;
import com.example.demo.dto.RateLimitEventMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "rocketmq", name = "enabled", havingValue = "true")
public class RocketMqRateLimitEventPublisher implements RateLimitEventPublisher {
    private static final Logger log =
            LoggerFactory.getLogger(RocketMqRateLimitEventPublisher.class);

    private final DefaultMQProducer producer;
    private final ObjectMapper objectMapper;
    private final RocketMqProperties properties;

    public RocketMqRateLimitEventPublisher(DefaultMQProducer producer, ObjectMapper objectMapper,
            RocketMqProperties properties) {
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void publish(RateLimitEventMessage event) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(event);
            Message message =
                    new Message(properties.topic(), event.eventType(), event.apiKey(), body);
            producer.send(message);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize rate limit event for apiKey={}", event.apiKey(), ex);
        } catch (Exception ex) {
            log.warn("Failed to publish rate limit event for apiKey={}", event.apiKey(), ex);
        }
    }
}
