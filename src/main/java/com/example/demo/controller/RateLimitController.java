package com.example.demo.controller;

import com.example.demo.config.PagingProperties;
import com.example.demo.dto.CheckAccessResponse;
import com.example.demo.dto.CreateLimitRequest;
import com.example.demo.dto.PagedLimitResponse;
import com.example.demo.dto.UsageResponse;
import com.example.demo.service.RateLimitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class RateLimitController {
    private final RateLimitService rateLimitService;
    private final PagingProperties pagingProperties;

    public RateLimitController(RateLimitService rateLimitService, PagingProperties pagingProperties) {
        this.rateLimitService = rateLimitService;
        this.pagingProperties = pagingProperties;
    }

    @PostMapping("/limits")
    @ResponseStatus(HttpStatus.CREATED)
    public void createLimit(@Valid @RequestBody CreateLimitRequest request) {
        rateLimitService.createOrUpdateLimit(request);
    }

    @GetMapping("/limits")
    public PagedLimitResponse getLimits(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int requestedPage = page == null ? pagingProperties.defaultPage() : page;
        int requestedSize = size == null ? pagingProperties.defaultSize() : size;
        return rateLimitService.getLimits(requestedPage, requestedSize);
    }

    @GetMapping("/check")
    public CheckAccessResponse checkAccess(@RequestParam String apiKey) {
        return rateLimitService.checkAccess(apiKey);
    }

    @GetMapping("/usage")
    public UsageResponse getUsage(@RequestParam String apiKey) {
        return rateLimitService.getUsage(apiKey);
    }

    @DeleteMapping("/limits/{apiKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLimit(@PathVariable String apiKey) {
        rateLimitService.deleteLimit(apiKey);
    }
}
