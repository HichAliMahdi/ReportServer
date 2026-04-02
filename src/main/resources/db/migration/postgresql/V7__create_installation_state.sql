CREATE TABLE IF NOT EXISTS installation_state (
    id BIGSERIAL PRIMARY KEY,
    admin_configured BOOLEAN NOT NULL DEFAULT FALSE,
    smtp_configured BOOLEAN NOT NULL DEFAULT FALSE,
    setup_completed BOOLEAN NOT NULL DEFAULT FALSE,
    setup_completed_at TIMESTAMP,
    updated_at TIMESTAMP
);
