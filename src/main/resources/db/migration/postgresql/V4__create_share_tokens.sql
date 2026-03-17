CREATE TABLE IF NOT EXISTS report_share_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(64) NOT NULL UNIQUE,
    report_file_name VARCHAR(500) NOT NULL,
    report_name VARCHAR(255) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_rst_token ON report_share_tokens(token);
CREATE INDEX IF NOT EXISTS idx_rst_report ON report_share_tokens(report_file_name);
