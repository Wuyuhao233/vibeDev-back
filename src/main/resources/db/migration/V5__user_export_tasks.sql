CREATE TABLE user_export_tasks (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    scope ENUM('all', 'posts', 'replies', 'collections') NOT NULL DEFAULT 'all',
    status ENUM('processing', 'ready', 'expired', 'error') NOT NULL DEFAULT 'processing',
    download_url VARCHAR(500) NULL,
    expires_at TIMESTAMP NULL,
    progress TINYINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_uet_user_id (user_id),
    INDEX idx_uet_status (status),
    CONSTRAINT fk_uet_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
