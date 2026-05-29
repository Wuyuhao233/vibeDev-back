-- Add ai_snippet column for AI audit violation snippet storage

ALTER TABLE moderation_queue
    ADD COLUMN ai_snippet VARCHAR(200) DEFAULT NULL
    AFTER board_id;
