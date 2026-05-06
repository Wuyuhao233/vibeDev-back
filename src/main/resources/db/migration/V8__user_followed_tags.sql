CREATE TABLE user_followed_tags (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    tag_id CHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_uft_user_tag (user_id, tag_id),
    INDEX idx_uft_user (user_id),
    INDEX idx_uft_tag (tag_id),
    CONSTRAINT fk_uft_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_uft_tag FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
