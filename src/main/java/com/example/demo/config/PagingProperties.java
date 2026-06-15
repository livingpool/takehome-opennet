package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rate-limit.paging")
public record PagingProperties(
        int defaultPage,
        int defaultSize,
        int maxSize) {
    public PagingProperties {
        if (defaultPage < 0) {
            defaultPage = 0;
        }
        if (defaultSize < 1) {
            defaultSize = 20;
        }
        if (maxSize < 1) {
            maxSize = 100;
        }
    }
}
