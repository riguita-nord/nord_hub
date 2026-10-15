CREATE TABLE github_deliveries (
    delivery_id VARCHAR(120) PRIMARY KEY,
    event_type VARCHAR(40) NOT NULL,
    repository_full_name VARCHAR(255),
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_github_deliveries_received ON github_deliveries(received_at DESC);
