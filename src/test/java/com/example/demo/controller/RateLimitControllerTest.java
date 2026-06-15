package com.example.demo.controller;

import com.example.demo.config.PagingProperties;
import com.example.demo.dto.CheckAccessResponse;
import com.example.demo.dto.LimitResponse;
import com.example.demo.dto.PagedLimitResponse;
import com.example.demo.dto.UsageResponse;
import com.example.demo.exception.RateLimitExceededException;
import com.example.demo.exception.RateLimitNotFoundException;
import com.example.demo.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RateLimitController.class)
@Import(RateLimitControllerTest.TestConfig.class)
class RateLimitControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateLimitService rateLimitService;

    // POST /limits

    @Test
    void createLimitReturns201_CreatedWithEmptyBody() throws Exception {
        mockMvc.perform(post("/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "apiKey": "abc-123",
                                  "limit": 100,
                                  "windowSeconds": 60
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        verify(rateLimitService).createOrUpdateLimit(argThat(request -> request.apiKey().equals("abc-123")
                && request.limit() == 100
                && request.windowSeconds() == 60));
    }

    @Test
    void createLimitRejects400_BlankApiKey() throws Exception {
        mockMvc.perform(post("/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "apiKey": "",
                                  "limit": 100,
                                  "windowSeconds": 60
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(rateLimitService);
    }

    @Test
    void createLimitRejects400_NonPositiveLimit() throws Exception {
        mockMvc.perform(post("/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "apiKey": "abc-123",
                                  "limit": 0,
                                  "windowSeconds": 60
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(rateLimitService);
    }

    @Test
    void createLimitRejects400_NonPositiveWindowSeconds() throws Exception {
        mockMvc.perform(post("/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "apiKey": "abc-123",
                                  "limit": 100,
                                  "windowSeconds": 0
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(rateLimitService);
    }

    // GET /limits

    @Test
    void getLimitsReturns200_PagedLimitsWithExplicitPagination() throws Exception {
        when(rateLimitService.getLimits(1, 2))
                .thenReturn(new PagedLimitResponse(
                        List.of(
                                new LimitResponse("abc-123", 100, 60),
                                new LimitResponse("def-456", 200, 120)),
                        1,
                        2,
                        5,
                        3));

        mockMvc.perform(get("/limits")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].apiKey").value("abc-123"))
                .andExpect(jsonPath("$.items[0].limit").value(100))
                .andExpect(jsonPath("$.items[0].windowSeconds").value(60))
                .andExpect(jsonPath("$.items[1].apiKey").value("def-456"))
                .andExpect(jsonPath("$.items[1].limit").value(200))
                .andExpect(jsonPath("$.items[1].windowSeconds").value(120))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        verify(rateLimitService).getLimits(1, 2);
    }

    @Test
    void getLimitsUsesConfiguredDefaults_WhenPaginationParamsAreMissing() throws Exception {
        when(rateLimitService.getLimits(0, 20))
                .thenReturn(new PagedLimitResponse(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/limits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        verify(rateLimitService).getLimits(0, 20);
    }

    // GET /check

    @Test
    void checkAccessReturns200_UsageResponse() throws Exception {
        when(rateLimitService.checkAccess("abc-123"))
                .thenReturn(new CheckAccessResponse(42, 58, 30));

        mockMvc.perform(get("/check")
                        .param("apiKey", "abc-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usage").value(42))
                .andExpect(jsonPath("$.remaining").value(58))
                .andExpect(jsonPath("$.ttlSeconds").value(30));

        verify(rateLimitService).checkAccess("abc-123");
    }

    @Test
    void checkAccessRejects400_MissingApiKey() throws Exception {
        mockMvc.perform(get("/check"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(rateLimitService);
    }

    @Test
    void checkAccessRejects404_ApiKeyNotFound() throws Exception {
        when(rateLimitService.checkAccess("missing-key"))
                .thenThrow(new RateLimitNotFoundException("missing-key"));

        mockMvc.perform(get("/check")
                        .param("apiKey", "missing-key"))
                .andExpect(status().isNotFound());

        verify(rateLimitService).checkAccess("missing-key");
    }

    @Test
    void checkAccessRejects429_RateLimitExceeded() throws Exception {
        when(rateLimitService.checkAccess("blocked-key"))
                .thenThrow(new RateLimitExceededException("blocked-key"));

        mockMvc.perform(get("/check")
                        .param("apiKey", "blocked-key"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").value("Rate limit exceeded for apiKey: blocked-key"));

        verify(rateLimitService).checkAccess("blocked-key");
    }

    // GET /usage

    @Test
    void getUsageReturns200_UsageResponse() throws Exception {
        when(rateLimitService.getUsage("abc-123"))
                .thenReturn(new UsageResponse(42, 58, 30));

        mockMvc.perform(get("/usage")
                        .param("apiKey", "abc-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usage").value(42))
                .andExpect(jsonPath("$.remaining").value(58))
                .andExpect(jsonPath("$.ttlSeconds").value(30));

        verify(rateLimitService).getUsage("abc-123");
    }

    @Test
    void getUsageRejects400_MissingApiKey() throws Exception {
        mockMvc.perform(get("/usage"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(rateLimitService);
    }

    @Test
    void getUsageRejects404_ApiKeyNotFound() throws Exception {
        when(rateLimitService.getUsage("missing-key"))
                .thenThrow(new RateLimitNotFoundException("missing-key"));

        mockMvc.perform(get("/usage")
                        .param("apiKey", "missing-key"))
                .andExpect(status().isNotFound());

        verify(rateLimitService).getUsage("missing-key");
    }

    // DELETE /limits/{apiKey}

    @Test
    void deleteLimitReturns204_NoContent() throws Exception {
        mockMvc.perform(delete("/limits/{apiKey}", "abc-123"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(rateLimitService).deleteLimit("abc-123");
    }

    static class TestConfig {
        @org.springframework.context.annotation.Bean
        PagingProperties pagingProperties() {
            return new PagingProperties(0, 20, 100);
        }
    }
}
