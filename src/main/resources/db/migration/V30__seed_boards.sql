-- Seed default boards
INSERT INTO boards (id, name, icon, description, sort_order, status, post_count) VALUES
  (UUID(), '综合讨论', '💬', '综合话题讨论区', 1, 'active', 0),
  (UUID(), '技术交流', '🔧', '技术问题交流与分享', 2, 'active', 0),
  (UUID(), '项目反馈', '📝', '项目建议与问题反馈', 3, 'active', 0),
  (UUID(), '资源分享', '📦', '学习资源与工具分享', 4, 'active', 0)
ON DUPLICATE KEY UPDATE name=name;
