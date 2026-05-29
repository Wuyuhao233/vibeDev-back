-- MariaDB doesn't support ngram parser. Keep default FULLTEXT indexes.
-- The existing idx_posts_title_content (V9) is already sufficient for basic FTS.
-- For CJK support on MariaDB, consider installing the Mroonga storage engine.

-- Only add the search_logs table since FULLTEXT indexes already exist from V9
CREATE TABLE IF NOT EXISTS search_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword VARCHAR(100) NOT NULL,
    scope VARCHAR(20) NOT NULL DEFAULT 'all',
    board_id CHAR(36) NULL,
    result_count INT NOT NULL DEFAULT 0,
    search_time_ms DOUBLE NOT NULL DEFAULT 0,
    ip_address VARCHAR(45) NULL,
    user_id CHAR(36) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_search_logs_created (created_at DESC),
    INDEX idx_search_logs_keyword (keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
