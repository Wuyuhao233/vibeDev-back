-- User follows: follow/unfollow other users
CREATE TABLE IF NOT EXISTS user_follows (
    id CHAR(36) COLLATE utf8mb4_unicode_ci NOT NULL PRIMARY KEY,
    user_id CHAR(36) COLLATE utf8mb4_unicode_ci NOT NULL,
    followed_user_id CHAR(36) COLLATE utf8mb4_unicode_ci NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_followed (user_id, followed_user_id),
    KEY idx_followed_user (followed_user_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (followed_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
