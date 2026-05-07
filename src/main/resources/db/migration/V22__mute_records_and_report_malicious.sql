CREATE TABLE mute_records (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    moderator_id CHAR(36) NOT NULL,
    board_id CHAR(36) NULL,
    duration VARCHAR(10) NOT NULL,
    reason TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    unmuted_by CHAR(36) NULL,
    unmuted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mr_user (user_id),
    INDEX idx_mr_active (is_active),
    CONSTRAINT fk_mr_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_mr_moderator FOREIGN KEY (moderator_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE reports ADD COLUMN is_malicious BOOLEAN NOT NULL DEFAULT FALSE AFTER processed_at;
