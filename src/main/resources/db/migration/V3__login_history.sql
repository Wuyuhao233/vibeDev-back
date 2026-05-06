CREATE TABLE login_history (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    login_type ENUM('email', 'cas') NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    user_agent VARCHAR(500) DEFAULT '',
    success BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_lh_user_id (user_id),
    INDEX idx_lh_created_at (created_at),
    CONSTRAINT fk_lh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
