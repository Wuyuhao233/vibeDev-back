-- Drop existing FULLTEXT index (without ngram parser, created in V9)
ALTER TABLE posts DROP INDEX idx_posts_title_content;

-- Recreate with ngram parser for Chinese text segmentation support
ALTER TABLE posts ADD FULLTEXT INDEX idx_posts_title_content (title, content_markdown) WITH PARSER ngram;

-- Title-only FULLTEXT index for scope=title_only searches
ALTER TABLE posts ADD FULLTEXT INDEX idx_posts_title (title) WITH PARSER ngram;

-- Search logs table for tracking search queries and measuring performance
CREATE TABLE search_logs (
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
