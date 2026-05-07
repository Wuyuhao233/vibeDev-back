CREATE TABLE points_log (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    amount INT NOT NULL,
    reason VARCHAR(50) NOT NULL,
    related_type VARCHAR(20) NULL,
    related_id CHAR(36) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_points_log_user (user_id, created_at DESC),
    INDEX idx_points_log_reason (reason),
    CONSTRAINT fk_points_log_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
