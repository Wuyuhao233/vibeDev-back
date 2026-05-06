CREATE TABLE collection_folders (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(50) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cf_user (user_id),
    UNIQUE INDEX idx_cf_user_name (user_id, name),
    CONSTRAINT fk_cf_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE favorites
    ADD CONSTRAINT fk_fav_folder
    FOREIGN KEY (collection_folder_id) REFERENCES collection_folders(id) ON DELETE SET NULL;
