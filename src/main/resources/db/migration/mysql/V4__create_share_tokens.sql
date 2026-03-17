CREATE TABLE IF NOT EXISTS report_share_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(64) NOT NULL UNIQUE,
    report_file_name VARCHAR(500) NOT NULL,
    report_name VARCHAR(255) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked TINYINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_rst_token ON report_share_tokens(token);
CREATE INDEX idx_rst_report ON report_share_tokens(report_file_name);
