CREATE TABLE IF NOT EXISTS report_share_recipients (
    id BIGINT NOT NULL AUTO_INCREMENT,
    report_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    shared_at DATETIME NOT NULL,
    shared_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_share_recipient_report
        FOREIGN KEY (report_id) REFERENCES shared_reports(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_share_recipient_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_report_share_recipients_report_user
        UNIQUE (report_id, user_id)
) ENGINE=InnoDB;
