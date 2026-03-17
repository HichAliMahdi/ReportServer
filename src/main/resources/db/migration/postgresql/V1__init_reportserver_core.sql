CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    reset_token VARCHAR(255),
    reset_token_expiry TIMESTAMP,
    role VARCHAR(255) NOT NULL DEFAULT 'READ_ONLY',
    first_login BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS datasources (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(255) NOT NULL,
    url VARCHAR(255),
    username VARCHAR(255),
    password VARCHAR(255),
    driver_class_name VARCHAR(255),
    file_path VARCHAR(255),
    configuration VARCHAR(2000),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS report_templates (
    id BIGSERIAL PRIMARY KEY,
    report_file_name VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    category VARCHAR(255),
    tags VARCHAR(1000),
    description VARCHAR(1000),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS scheduled_reports (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    report_name VARCHAR(255) NOT NULL,
    format VARCHAR(255) NOT NULL,
    schedule_type VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(120),
    datasource_id BIGINT,
    enabled BOOLEAN,
    last_run_time TIMESTAMP,
    next_run_time TIMESTAMP,
    output_path VARCHAR(255),
    description VARCHAR(2000),
    parameters VARCHAR(4000),
    day_of_week INTEGER,
    day_of_month INTEGER,
    month_of_year INTEGER,
    hour_of_day INTEGER,
    minute_of_hour INTEGER,
    created_by VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS shared_reports (
    id BIGSERIAL PRIMARY KEY,
    report_file_name VARCHAR(255) NOT NULL UNIQUE,
    report_name VARCHAR(255) NOT NULL,
    report_format VARCHAR(255) NOT NULL,
    category VARCHAR(255),
    tags VARCHAR(1000),
    shared_with_readonly BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    shared_at TIMESTAMP,
    shared_by VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS api_keys (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    key_hash VARCHAR(500) NOT NULL UNIQUE,
    plain_key VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP,
    last_used_timestamp BIGINT NOT NULL DEFAULT 0,
    permissions TEXT
);

CREATE TABLE IF NOT EXISTS workspaces (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    owner_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    members TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_workspace_name_owner UNIQUE (name, owner_id)
);

CREATE TABLE IF NOT EXISTS report_delivery_options (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    recipient_or_url VARCHAR(500) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    metadata TEXT
);

CREATE TABLE IF NOT EXISTS report_thumbnails (
    id BIGSERIAL PRIMARY KEY,
    report_template_id BIGINT NOT NULL,
    thumbnail_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    generated_at TIMESTAMP NOT NULL,
    valid BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_report_template_id ON report_thumbnails(report_template_id);
CREATE INDEX IF NOT EXISTS idx_created_at ON report_thumbnails(created_at);

CREATE TABLE IF NOT EXISTS report_execution_logs (
    id BIGSERIAL PRIMARY KEY,
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
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    duration_ms BIGINT
);

CREATE INDEX IF NOT EXISTS idx_report_execution_started_at ON report_execution_logs(started_at);
CREATE INDEX IF NOT EXISTS idx_report_execution_report_name ON report_execution_logs(report_name);
CREATE INDEX IF NOT EXISTS idx_report_execution_executed_by ON report_execution_logs(executed_by);
