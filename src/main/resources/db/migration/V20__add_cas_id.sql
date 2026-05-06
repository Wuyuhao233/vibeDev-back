ALTER TABLE users ADD COLUMN cas_id VARCHAR(100) NULL AFTER recovery_deadline;
CREATE INDEX idx_users_cas_id ON users(cas_id);
