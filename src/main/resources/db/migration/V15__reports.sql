CREATE TABLE reports (
    id CHAR(36) PRIMARY KEY,
    reporter_id CHAR(36) NOT NULL,
    target_type ENUM('post', 'reply') NOT NULL,
    target_id CHAR(36) NOT NULL,
    reason_type ENUM('spam', 'personal_attack', 'violation', 'other') NOT NULL,
    description VARCHAR(500) NULL,
    status ENUM('pending', 'processed') NOT NULL DEFAULT 'pending',
    result ENUM('ignored', 'deleted', 'banned') NULL,
    result_description VARCHAR(500) NULL,
    handler_id CHAR(36) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    UNIQUE INDEX idx_reports_reporter_target (reporter_id, target_type, target_id),
    INDEX idx_reports_status (status),
    INDEX idx_reports_created (created_at DESC),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
    CONSTRAINT fk_reports_handler FOREIGN KEY (handler_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
