CREATE TABLE system_configs (
    id CHAR(36) PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT NOT NULL,
    description VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_sc_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO system_configs (id, config_key, config_value, description) VALUES
(UUID(), 'recommendation_weights', '{"tag_match":0.4,"heat":0.3,"freshness":0.2,"quality":0.1}', 'Recommendation engine weights'),
(UUID(), 'ai_threshold_high', '85', 'AI moderation high-risk threshold'),
(UUID(), 'ai_threshold_low', '50', 'AI moderation low-risk threshold'),
(UUID(), 'monthly_budget_cap', '200', 'Monthly AI moderation budget cap in CNY'),
(UUID(), 'points_rules', '{"post_create":5,"reply_create":2,"post_liked":1,"reply_liked":1,"post_collected":3,"daily_checkin":2,"post_essenced":10}', 'Points earning rules'),
(UUID(), 'sensitive_words_enabled', 'true', 'Sensitive word filter toggle'),
(UUID(), 'registration_enabled', 'true', 'New user registration toggle');
