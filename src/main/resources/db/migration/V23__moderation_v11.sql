-- V23: Moderation V1.1 — AI review queue, moderation log, AI audit log
CREATE TABLE IF NOT EXISTS `moderation_queue` (
    `id` VARCHAR(36) NOT NULL,
    `target_type` VARCHAR(10) NOT NULL,
    `target_id` VARCHAR(36) NOT NULL,
    `content_snapshot` TEXT,
    `ai_score` INT DEFAULT 0,
    `ai_category` VARCHAR(50),
    `ai_degraded` TINYINT DEFAULT 0,
    `priority` INT DEFAULT 0,
    `status` VARCHAR(10) NOT NULL DEFAULT 'pending',
    `moderator_id` VARCHAR(36),
    `reason` TEXT,
    `board_id` VARCHAR(36),
    `author_id` VARCHAR(36),
    `target_title` VARCHAR(100),
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `handled_at` DATETIME(3),
    PRIMARY KEY (`id`),
    INDEX `idx_mq_status_priority` (`status`, `priority` DESC, `created_at`),
    INDEX `idx_mq_board` (`board_id`),
    INDEX `idx_mq_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `moderation_log` (
    `id` VARCHAR(36) NOT NULL,
    `target_type` VARCHAR(10) NOT NULL,
    `target_id` VARCHAR(36) NOT NULL,
    `action` VARCHAR(20) NOT NULL,
    `operator_id` VARCHAR(36),
    `reason` TEXT,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    INDEX `idx_ml_target` (`target_type`, `target_id`),
    INDEX `idx_ml_action` (`action`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_audit_log` (
    `id` VARCHAR(36) NOT NULL,
    `target_type` VARCHAR(10) NOT NULL,
    `target_id` VARCHAR(36) NOT NULL,
    `api_provider` VARCHAR(50) DEFAULT 'mock',
    `request_chars` INT DEFAULT 0,
    `response_score` INT DEFAULT 0,
    `response_category` VARCHAR(50),
    `is_degraded` TINYINT DEFAULT 0,
    `cost` DECIMAL(10, 6) DEFAULT 0.000000,
    `response_time_ms` INT DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    INDEX `idx_aal_created` (`created_at`),
    INDEX `idx_aal_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
