package com.example.demo.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String apiKey) {
        super("Rate limit exceeded for apiKey: " + apiKey);
    }
}
