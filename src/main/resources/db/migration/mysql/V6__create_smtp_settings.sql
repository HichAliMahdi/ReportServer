CREATE TABLE IF NOT EXISTS smtp_settings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL DEFAULT 587,
    username VARCHAR(255),
    password_encrypted VARCHAR(1000),
    from_email VARCHAR(255),
    auth_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    starttls_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    starttls_required BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at DATETIME,
    updated_by VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE=InnoDB;
