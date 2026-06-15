package com.example.demo.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.demo.dto.RateLimitExceededResponse;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public RateLimitExceededResponse handleRateLimitExceeded(RateLimitExceededException ex) {
        return new RateLimitExceededResponse(Instant.now(), ex.getMessage());
    }
}
