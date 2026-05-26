#!/bin/bash
set -e

echo "🔨 构建镜像..."
podman build -t vibedev-backend:latest .

echo "🛑 停止旧容器..."
podman rm -f vibedev-backend 2>/dev/null || true

echo "🚀 启动新容器（含 uploads 挂载）..."
podman run -d --pod vibedev-pod --name vibedev-backend \
  -e DB_PASSWORD=root \
  -v "$(dirname "$0")/uploads:/app/uploads:Z" \
  vibedev-backend:latest

echo ""
echo "⏳ 等待服务就绪..."
for i in $(seq 1 15); do
  if curl -s -o /dev/null http://localhost:8081/api/v1/health 2>/dev/null; then
    echo "✅ 后端服务已就绪"
    exit 0
  fi
  sleep 1
done

echo "❌ 服务启动超时，请检查日志: podman logs vibedev-backend"
exit 1
