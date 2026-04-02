CREATE TABLE IF NOT EXISTS installation_state (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_configured BOOLEAN NOT NULL DEFAULT FALSE,
    smtp_configured BOOLEAN NOT NULL DEFAULT FALSE,
    setup_completed BOOLEAN NOT NULL DEFAULT FALSE,
    setup_completed_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB;
