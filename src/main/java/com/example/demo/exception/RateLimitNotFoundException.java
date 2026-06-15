package com.example.demo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RateLimitNotFoundException extends RuntimeException {
    public RateLimitNotFoundException(String apiKey) {
        super("No rate limit configured for apiKey: " + apiKey);
    }
}
