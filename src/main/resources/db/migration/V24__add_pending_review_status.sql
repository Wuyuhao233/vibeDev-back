-- V24: Add pending_review to audit_status ENUM
ALTER TABLE posts MODIFY COLUMN audit_status ENUM('pending', 'pending_review', 'approved', 'rejected') NOT NULL DEFAULT 'approved';
ALTER TABLE replies MODIFY COLUMN audit_status ENUM('pending', 'pending_review', 'approved', 'rejected') NOT NULL DEFAULT 'approved';
