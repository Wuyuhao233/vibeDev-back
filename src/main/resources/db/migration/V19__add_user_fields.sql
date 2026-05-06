ALTER TABLE users
    ADD COLUMN login_fail_count INT NOT NULL DEFAULT 0,
    ADD COLUMN login_locked_until TIMESTAMP NULL,
    ADD COLUMN is_deactivated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deactivated_at TIMESTAMP NULL,
    ADD COLUMN recovery_deadline TIMESTAMP NULL;
