-- Replace existing boards with the new navigation items
-- 1. Clean up dependent data (order matters due to FK constraints)

-- Tables with target_type/target_id pattern
DELETE FROM likes WHERE target_type = 'post' AND target_id IN (SELECT id FROM posts);
DELETE FROM reports WHERE target_type = 'post' AND target_id IN (SELECT id FROM posts);
DELETE FROM moderation_queue WHERE target_type = 'post' AND target_id IN (SELECT id FROM posts);
DELETE FROM moderation_log WHERE target_type = 'post' AND target_id IN (SELECT id FROM posts);
DELETE FROM ai_audit_log WHERE target_type = 'post' AND target_id IN (SELECT id FROM posts);

-- Tables with source_type/source_id pattern
DELETE FROM notifications WHERE source_type = 'post' AND source_id IN (SELECT id FROM posts);

-- Tables with direct post_id FK
DELETE FROM post_tags WHERE post_id IN (SELECT id FROM posts);
DELETE FROM replies WHERE post_id IN (SELECT id FROM posts);
DELETE FROM favorites WHERE post_id IN (SELECT id FROM posts);

-- Now safe to delete posts
DELETE FROM posts WHERE board_id IN (SELECT id FROM boards);

-- Tags cascade on board delete, but delete explicitly for clarity
DELETE FROM tags WHERE board_id IN (SELECT id FROM boards);

-- Delete old boards
DELETE FROM boards;

-- 2. Insert new boards
INSERT INTO boards (id, name, icon, description, sort_order, status, post_count) VALUES
  (UUID(), '关注', '⭐', '关注的内容动态',  1,  'active', 0),
  (UUID(), '综合', '🧭', '综合话题讨论区',  2,  'active', 0),
  (UUID(), '后端', '📊', '后端开发与技术',  3,  'active', 0),
  (UUID(), '前端', '💻', '前端开发与技术',  4,  'active', 0),
  (UUID(), 'Android', '🤖', 'Android 开发', 5,  'active', 0),
  (UUID(), 'iOS', '🍎', 'iOS 开发',        6,  'active', 0),
  (UUID(), '人工智能', '🧠', 'AI 与机器学习', 7, 'active', 0),
  (UUID(), '开发工具', '🔧', '开发工具与效率', 8, 'active', 0),
  (UUID(), '代码人生', '📄', '程序员生活分享', 9, 'active', 0),
  (UUID(), '阅读', '📖', '技术阅读与笔记',  10, 'active', 0),
  (UUID(), '排行榜', '🏆', '热门内容排行',   11, 'active', 0);
