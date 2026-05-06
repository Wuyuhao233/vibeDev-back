CREATE TABLE sensitive_words (
    id CHAR(36) PRIMARY KEY,
    word VARCHAR(50) NOT NULL,
    match_type ENUM('exact', 'fuzzy') NOT NULL DEFAULT 'fuzzy',
    category VARCHAR(50) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_sw_word (word),
    INDEX idx_sw_active (is_active),
    INDEX idx_sw_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
