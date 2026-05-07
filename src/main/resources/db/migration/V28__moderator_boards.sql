CREATE TABLE moderator_boards (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    board_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_board (user_id, board_id),
    KEY idx_user_id (user_id),
    KEY idx_board_id (board_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
