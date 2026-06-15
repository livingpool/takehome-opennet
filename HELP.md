# Rate Limiter API Help

## Quick Start (Everything in Docker)

Start all services including the app:

```bash
docker compose up -d --build
```

This starts MySQL, Redis, RocketMQ (namesrv + broker + console), and the Spring Boot app.
The app is configured with `rocketmq.enabled=true` by default.

Services:

| Service | URL |
|---|---|
| App API | http://localhost:8080 |
| RocketMQ Dashboard | http://localhost:8088 |

## Run App Locally (without Docker)

```bash
# Start infrastructure only
docker compose up -d mysql redis rocketmq-namesrv rocketmq-broker rocketmq-console

# Run the app on your host
./mvnw spring-boot:run -Dspring-boot.run.arguments="--rocketmq.enabled=true"
```

## Run Tests

```bash
./mvnw test
```

The integration test (`RateLimitIntegrationTest`) uses Testcontainers to spin up
real MySQL and Redis containers automatically. RocketMQ is not needed for tests
(it defaults to `NoopRateLimitEventPublisher`).

## Create Or Update Limit

```bash
curl -i -X POST 'http://localhost:8080/limits' \
  -H 'Content-Type: application/json' \
  -d '{"apiKey":"curl-demo","limit":3,"windowSeconds":60}'
```

Expected response:

```text
HTTP/1.1 201
Content-Length: 0
```

## List Limits

```bash
curl -i 'http://localhost:8080/limits?page=0&size=5'
```

Example response after creating `curl-demo` with seeded data present:

```json
{
  "items": [
    {
      "apiKey": "demo-free",
      "limit": 10,
      "windowSeconds": 60
    },
    {
      "apiKey": "demo-pro",
      "limit": 100,
      "windowSeconds": 60
    },
    {
      "apiKey": "demo-burst",
      "limit": 20,
      "windowSeconds": 10
    },
    {
      "apiKey": "abc-123",
      "limit": 100,
      "windowSeconds": 60
    },
    {
      "apiKey": "curl-demo",
      "limit": 3,
      "windowSeconds": 60
    }
  ],
  "page": 0,
  "size": 5,
  "totalItems": 5,
  "totalPages": 1
}
```

## Check API Access

```bash
curl -i 'http://localhost:8080/check?apiKey=curl-demo'
```

Expected first response:

```json
{
  "usage": 1,
  "remaining": 2,
  "ttlSeconds": 60
}
```

Calling `/check` again increments usage:

```bash
curl -i 'http://localhost:8080/check?apiKey=curl-demo'
```

Example response:

```json
{
  "usage": 2,
  "remaining": 1,
  "ttlSeconds": 53
}
```

The third request reaches the quota:

```bash
curl -i 'http://localhost:8080/check?apiKey=curl-demo'
```

Example response:

```json
{
  "usage": 3,
  "remaining": 0,
  "ttlSeconds": 60
}
```

The fourth request is blocked:

```bash
curl -i 'http://localhost:8080/check?apiKey=curl-demo'
```

Expected response:

```text
HTTP/1.1 429
```

Example body:

```json
{
  "timestamp": "2026-06-14T02:42:19.249+00:00",
  "status": 429
}
```

## Query Current Usage

```bash
curl -i 'http://localhost:8080/usage?apiKey=curl-demo'
```

Example response:

```json
{
  "usage": 4,
  "remaining": 0,
  "ttlSeconds": 60
}
```

`/usage` reads the current Redis counter without incrementing it.

## Delete Limit

```bash
curl -i -X DELETE 'http://localhost:8080/limits/curl-demo'
```

Expected response:

```text
HTTP/1.1 204
```

After deletion, related Redis config and usage keys are cleared.

## Missing API Key Rule

After deleting a rate limit, checking or querying usage for that apiKey returns `404`:

```bash
curl -i 'http://localhost:8080/check?apiKey=curl-demo'
```

```bash
curl -i 'http://localhost:8080/usage?apiKey=curl-demo'
```
