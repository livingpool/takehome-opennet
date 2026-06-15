CREATE TABLE IF NOT EXISTS rate_limit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_key VARCHAR(255) NOT NULL UNIQUE,
    request_limit INT NOT NULL,
    window_seconds INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO rate_limit (api_key, request_limit, window_seconds)
VALUES
    ('demo-free', 10, 60),
    ('demo-pro', 100, 60),
    ('demo-burst', 20, 10),
    ('abc-123', 100, 60)
ON DUPLICATE KEY UPDATE
    request_limit = VALUES(request_limit),
    window_seconds = VALUES(window_seconds),
    updated_at = CURRENT_TIMESTAMP;
