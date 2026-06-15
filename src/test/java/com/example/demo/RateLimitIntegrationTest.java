package com.example.demo;

import com.example.demo.dto.CheckAccessResponse;
import com.example.demo.dto.CreateLimitRequest;
import com.example.demo.dto.PagedLimitResponse;
import com.example.demo.dto.UsageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RateLimitIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("taskdb")
            .withUsername("taskuser")
            .withPassword("taskpass")
            .withInitScript("init.sql");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void getLimitsReturnsNonEmptyList() {
        ResponseEntity<PagedLimitResponse> response = restTemplate
                .getForEntity("/limits", PagedLimitResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().items()).isNotEmpty();
    }

    @Test
    void createLimitThenListReflectsNewRule() {
        CreateLimitRequest request = new CreateLimitRequest("it-create-key-" + System.currentTimeMillis(), 50, 30);

        ResponseEntity<Void> createResponse = restTemplate
                .postForEntity("/limits", request, Void.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<PagedLimitResponse> listResponse = restTemplate
                .getForEntity("/limits", PagedLimitResponse.class);

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();
        assertThat(listResponse.getBody().items())
                .anyMatch(item -> item.apiKey().equals(request.apiKey()));
    }

    @Test
    void checkAccessIncrementsUsageAndReturnsOk() {
        String apiKey = "it-check-key-" + System.currentTimeMillis();
        restTemplate.postForEntity("/limits", new CreateLimitRequest(apiKey, 100, 60), Void.class);

        ResponseEntity<CheckAccessResponse> first = restTemplate
                .getForEntity("/check?apiKey={key}", CheckAccessResponse.class, apiKey);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().usage()).isEqualTo(1);
        assertThat(first.getBody().remaining()).isEqualTo(99);
        assertThat(first.getBody().ttlSeconds()).isPositive();

        ResponseEntity<CheckAccessResponse> second = restTemplate
                .getForEntity("/check?apiKey={key}", CheckAccessResponse.class, apiKey);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().usage()).isEqualTo(2);
        assertThat(second.getBody().remaining()).isEqualTo(98);
    }

    @Test
    void checkAccessBlocksWhenLimitExceeded() {
        String apiKey = "it-block-key-" + System.currentTimeMillis();
        restTemplate.postForEntity("/limits", new CreateLimitRequest(apiKey, 1, 60), Void.class);

        ResponseEntity<CheckAccessResponse> first = restTemplate
                .getForEntity("/check?apiKey={key}", CheckAccessResponse.class, apiKey);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<CheckAccessResponse> second = restTemplate
                .getForEntity("/check?apiKey={key}", CheckAccessResponse.class, apiKey);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void getUsageReturnsRemainingQuota() {
        String apiKey = "it-usage-key-" + System.currentTimeMillis();
        restTemplate.postForEntity("/limits", new CreateLimitRequest(apiKey, 100, 60), Void.class);

        restTemplate.getForEntity("/check?apiKey={key}", CheckAccessResponse.class, apiKey);
        restTemplate.getForEntity("/check?apiKey={key}", CheckAccessResponse.class, apiKey);
        restTemplate.getForEntity("/check?apiKey={key}", CheckAccessResponse.class, apiKey);

        ResponseEntity<UsageResponse> usageResponse = restTemplate
                .getForEntity("/usage?apiKey={key}", UsageResponse.class, apiKey);

        assertThat(usageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(usageResponse.getBody()).isNotNull();
        assertThat(usageResponse.getBody().usage()).isEqualTo(3);
        assertThat(usageResponse.getBody().remaining()).isEqualTo(97);
        assertThat(usageResponse.getBody().ttlSeconds()).isPositive();
    }

    @Test
    void checkAccessReturns404ForUnknownApiKey() {
        ResponseEntity<String> response = restTemplate
                .getForEntity("/check?apiKey=nonexistent", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteLimitRemovesRuleAndCheckReturns404() {
        String apiKey = "it-delete-key-" + System.currentTimeMillis();
        restTemplate.postForEntity("/limits", new CreateLimitRequest(apiKey, 100, 60), Void.class);

        restTemplate.delete("/limits/{apiKey}", apiKey);

        ResponseEntity<String> checkResponse = restTemplate
                .getForEntity("/check?apiKey={key}", String.class, apiKey);

        assertThat(checkResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getLimitsSupportsPagination() {
        ResponseEntity<PagedLimitResponse> page = restTemplate
                .getForEntity("/limits?page=0&size=2", PagedLimitResponse.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).isNotNull();
        assertThat(page.getBody().items()).hasSize(2);
        assertThat(page.getBody().page()).isEqualTo(0);
        assertThat(page.getBody().size()).isEqualTo(2);
    }

    @Test
    void createLimitWithEmptyApiKeyReturns400() {
        CreateLimitRequest request = new CreateLimitRequest("", 100, 60);

        ResponseEntity<String> response = restTemplate
                .postForEntity("/limits", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createLimitWithZeroLimitReturns400() {
        CreateLimitRequest request = new CreateLimitRequest("test-key", 0, 60);

        ResponseEntity<String> response = restTemplate
                .postForEntity("/limits", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
