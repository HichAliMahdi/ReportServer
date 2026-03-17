CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME,
    updated_at DATETIME,
    reset_token VARCHAR(255),
    reset_token_expiry DATETIME,
    role VARCHAR(255) NOT NULL DEFAULT 'READ_ONLY',
    first_login BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS datasources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    url VARCHAR(255),
    username VARCHAR(255),
    password VARCHAR(255),
    driver_class_name VARCHAR(255),
    file_path VARCHAR(255),
    configuration VARCHAR(2000),
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_datasources_name (name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS report_templates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    report_file_name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    category VARCHAR(255),
    tags VARCHAR(1000),
    description VARCHAR(1000),
    created_by VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_templates_report_file_name (report_file_name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS scheduled_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    report_name VARCHAR(255) NOT NULL,
    format VARCHAR(255) NOT NULL,
    schedule_type VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(120),
    datasource_id BIGINT,
    enabled BOOLEAN,
    last_run_time DATETIME,
    next_run_time DATETIME,
    output_path VARCHAR(255),
    description VARCHAR(2000),
    parameters VARCHAR(4000),
    day_of_week INT,
    day_of_month INT,
    month_of_year INT,
    hour_of_day INT,
    minute_of_hour INT,
    created_by VARCHAR(255),
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS shared_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    report_file_name VARCHAR(255) NOT NULL,
    report_name VARCHAR(255) NOT NULL,
    report_format VARCHAR(255) NOT NULL,
    category VARCHAR(255),
    tags VARCHAR(1000),
    shared_with_readonly BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    shared_at DATETIME,
    shared_by VARCHAR(255),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shared_reports_report_file_name (report_file_name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS api_keys (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    key_hash VARCHAR(500) NOT NULL,
    plain_key VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    last_used_at DATETIME,
    last_used_timestamp BIGINT NOT NULL DEFAULT 0,
    permissions LONGTEXT,
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_keys_key_hash (key_hash)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS workspaces (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    owner_id VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    members LONGTEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workspace_name_owner (name, owner_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS report_delivery_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    recipient_or_url VARCHAR(500) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    metadata LONGTEXT,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS report_thumbnails (
    id BIGINT NOT NULL AUTO_INCREMENT,
    report_template_id BIGINT NOT NULL,
    thumbnail_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    generated_at DATETIME NOT NULL,
    valid BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE INDEX idx_report_template_id ON report_thumbnails(report_template_id);
CREATE INDEX idx_created_at ON report_thumbnails(created_at);

CREATE TABLE IF NOT EXISTS report_execution_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    report_name VARCHAR(255) NOT NULL,
    format VARCHAR(255) NOT NULL,
    execution_type VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    executed_by VARCHAR(255),
    datasource_id BIGINT,
    schedule_id BIGINT,
    parameters_json VARCHAR(4000),
    output_file_name VARCHAR(1000),
    error_message VARCHAR(4000),
    started_at DATETIME NOT NULL,
    completed_at DATETIME,
    duration_ms BIGINT,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE INDEX idx_report_execution_started_at ON report_execution_logs(started_at);
CREATE INDEX idx_report_execution_report_name ON report_execution_logs(report_name);
CREATE INDEX idx_report_execution_executed_by ON report_execution_logs(executed_by);
