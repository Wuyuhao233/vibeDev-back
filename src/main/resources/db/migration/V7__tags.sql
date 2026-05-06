CREATE TABLE tags (
    id CHAR(36) PRIMARY KEY,
    board_id CHAR(36) NOT NULL,
    name VARCHAR(10) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    post_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tags_board (board_id),
    UNIQUE INDEX idx_tags_board_name (board_id, name),
    CONSTRAINT fk_tags_board FOREIGN KEY (board_id) REFERENCES boards(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
