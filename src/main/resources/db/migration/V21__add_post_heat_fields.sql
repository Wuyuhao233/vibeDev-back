ALTER TABLE posts
    ADD COLUMN heat_score DECIMAL(10,4) NOT NULL DEFAULT 0,
    ADD COLUMN content_length INT NOT NULL DEFAULT 0,
    ADD COLUMN has_code_block TINYINT(1) NOT NULL DEFAULT 0;

CREATE INDEX idx_posts_heat ON posts (heat_score DESC);
