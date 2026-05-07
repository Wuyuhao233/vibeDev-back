CREATE TABLE IF NOT EXISTS appeals (
    id VARCHAR(36) NOT NULL,
    report_id VARCHAR(36) NOT NULL,
    appellant_id VARCHAR(36) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'pending',
    handler_id VARCHAR(36),
    handler_note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_appeals_report_id (report_id),
    INDEX idx_appeals_appellant_id (appellant_id),
    INDEX idx_appeals_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
