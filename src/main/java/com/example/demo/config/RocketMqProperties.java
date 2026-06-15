package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rocketmq")
public record RocketMqProperties(boolean enabled, String nameServer, String producerGroup,
                                 String topic, int sendTimeoutMs) {
    public RocketMqProperties {
        if (nameServer == null || nameServer.isBlank()) {
            nameServer = "localhost:9876";
        }
        if (producerGroup == null || producerGroup.isBlank()) {
            producerGroup = "rate-limit-producer-group";
        }
        if (topic == null || topic.isBlank()) {
            topic = "rate-limit-events";
        }
        if (sendTimeoutMs < 1) {
            sendTimeoutMs = 3000;
        }
    }
}
