package com.example.demo.config;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMqProducerConfig {
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "rocketmq", name = "enabled", havingValue = "true")
    DefaultMQProducer defaultMqProducer(RocketMqProperties properties) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer(properties.producerGroup());
        producer.setNamesrvAddr(properties.nameServer());
        producer.setSendMsgTimeout(properties.sendTimeoutMs());
        producer.setUnitName("rate-limit-service");
        producer.setInstanceName("rate-limit-service-producer");
        producer.setVipChannelEnabled(false);
        producer.setNamespace(null);
        producer.setCreateTopicKey(properties.topic());
        producer.setSendLatencyFaultEnable(true);
        producer.start();
        return producer;
    }
}
