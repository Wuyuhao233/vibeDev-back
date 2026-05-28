# vibeDev 后端 Podman 部署指南

## 部署架构

```
┌──────────────────────────────────────────────────┐
│  vibedev-pod (Pod)                                │
│  -p 8081:8080  (宿主机:容器)                       │
│                                                    │
│  ┌──────────────┐  ┌──────────────┐               │
│  │ vibedev-mysql │  │ vibedev-redis│               │
│  │   MySQL 8.0   │  │  Redis 7     │               │
│  │   :3306       │  │  :6379       │               │
│  └──────┬───────┘  └──────┬───────┘               │
│         │                 │                        │
│         │   localhost     │                        │
│         └────────┬────────┘                        │
│                  ▼                                  │
│         ┌───────────────┐                          │
│         │vibedev-backend│                          │
│         │ Spring Boot   │                          │
│         │  :8080        │                          │
│         └───────────────┘                          │
│              │                                      │
│   -v ./uploads:/app/uploads:Z                      │
└──────────────┴───────────────────────────────────┘
```

Pod 内所有容器共享同一个网络命名空间（`localhost`），Backend 通过 `localhost:3306` 访问 MySQL、`localhost:6379` 访问 Redis，无需额外配置。

---

## 一、首次部署

### 1.1 拉取基础镜像

```bash
podman pull docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17
podman pull docker.m.daocloud.io/library/eclipse-temurin:17-jre
podman pull docker.m.daocloud.io/library/mysql:8.0
podman pull docker.m.daocloud.io/library/redis:7-alpine
```

### 1.2 构建 Backend 镜像

```bash
cd backend
podman build -t vibedev-backend:latest .
```

> **构建速度说明：**
> - **正常开发**：`podman build`（不加 `--no-cache`）约 15-20 秒，利用 Docker 层缓存跳过依赖下载
> - **强制全量构建**：`podman build --no-cache` 约 3-5 分钟，重新下载所有依赖，仅在 `pom.xml` 变更或缓存异常时使用
> - `podman restart` 不会使用新镜像，必须删除旧容器后用新镜像重建

### 1.3 创建 Pod

```bash
# 映射宿主机 8081 到容器内 8080（避让本地 8080）
podman pod create --name vibedev-pod -p 8081:8080
```

### 1.4 启动 MySQL

```bash
podman run -d --pod vibedev-pod --name vibedev-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=vibedev \
  -e MYSQL_CHARACTER_SET_SERVER=utf8mb4 \
  -e MYSQL_COLLATION_SERVER=utf8mb4_unicode_ci \
  docker.m.daocloud.io/library/mysql:8.0
```

### 1.5 启动 Redis

```bash
podman run -d --pod vibedev-pod --name vibedev-redis \
  docker.m.daocloud.io/library/redis:7-alpine
```

### 1.6 启动 Backend

```bash
mkdir -p backend/uploads
podman run -d --pod vibedev-pod --name vibedev-backend \
  -e DB_PASSWORD=root \
  -v ./uploads:/app/uploads:Z \
  vibedev-backend:latest
```

> ⚠️ 务必带上 `-v ./uploads:/app/uploads:Z`，否则头像/帖子图片等上传文件会丢失。Pod 内容器共享网络，`REDIS_HOST` 默认 `localhost` 即可访问同 Pod 内的 Redis。

### 1.7 验证部署

```bash
# 健康检查
curl -s -w "\nHTTP: %{http_code}" http://localhost:8081/api/v1/health

# 查看容器状态
podman ps --format "{{.Names}} {{.Status}}" | grep vibedev
```

---

## 二、日常启动/停止

### 启动所有服务

```bash
podman pod start vibedev-pod
```

### 停止所有服务

```bash
podman pod stop vibedev-pod
```

---

## 三、后端代码变更后重建

修改后端代码后需重新构建镜像并重建容器。

### 方式一：一键脚本（推荐）

```bash
cd backend
bash rebuild.sh
```

该脚本自动完成：**构建镜像 → 删除旧容器 → 新建容器（带 uploads 挂载）→ 等待健康检查通过**。

### 方式二：手动分步执行

```bash
cd backend

# 1. 构建新镜像
podman build -t vibedev-backend:latest .

# 2. 删除旧容器
podman rm -f vibedev-backend

# 3. 启动新容器（务必带 uploads 挂载）
podman run -d --name vibedev-backend --pod vibedev-pod \
  -e DB_PASSWORD=root \
  -v ./uploads:/app/uploads:Z \
  vibedev-backend:latest

# 4. 等待就绪（约 20 秒）
sleep 20
curl -s -w "\nHTTP: %{http_code}" http://localhost:8081/api/v1/health
```

### 验证新镜像是否生效

```bash
# 健康检查
curl -s -w "\nHTTP: %{http_code}" http://localhost:8081/api/v1/health

# 检查容器状态
podman ps --format "{{.Names}} {{.Status}}" | grep backend

# 查看启动日志（排查问题用）
podman logs --tail 30 vibedev-backend
```

---

## 四、故障排查

### 容器异常退出

```bash
# 查看退出日志
podman logs --tail 50 vibedev-backend

# 检查 Pod 状态
podman pod ps

# 检查所有容器状态
podman ps -a --format "{{.Names}} {{.Status}} {{.Ports}}" | grep vibedev
```

### Backend 单独重建（Pod/Mysql/Redis 正常时）

```bash
podman rm -f vibedev-backend
podman run -d --name vibedev-backend --pod vibedev-pod \
  -e DB_PASSWORD=root \
  -v ./uploads:/app/uploads:Z \
  vibedev-backend:latest
```

### 完全重建 Pod

```bash
# 删除整个 Pod（含所有容器）
podman pod rm -f vibedev-pod

# 然后从 "一、首次部署" 的 1.3 步骤开始重新执行
```

---

## 五、关键配置说明

| 配置项 | 位置 | 说明 |
|--------|------|------|
| 后端应用配置 | `backend/src/main/resources/application.yml` | 数据库、Redis、JWT、邮件、CORS、AI 审核等 |
| Maven 镜像源 | `backend/settings.xml` | 阿里云 Maven 镜像（容器构建必需） |
| 容器构建文件 | `backend/Containerfile` | 多阶段构建：Maven 编译 → JRE 运行 |
| 开发构建文件 | `backend/Containerfile.dev` | 单阶段 JRE 运行（用于本地编译后的 jar） |
| 一键重建脚本 | `backend/rebuild.sh` | 构建镜像 + 重建容器 + 健康检查 |
| 数据库迁移 | `backend/src/main/resources/db/migration/` | Flyway 管理，启动时自动执行 |
| 上传文件目录 | `backend/uploads/` | 挂载到容器 `/app/uploads` |
| 端口映射 | Pod 创建时指定 | 宿主机 `8081` → 容器 `8080` |

## 六、环境变量参考

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_PASSWORD` | `root` | MySQL 连接密码 |
| `REDIS_HOST` | `localhost` | Redis 地址（Pod 内用 localhost） |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | (空) | Redis 密码 |
| `JWT_SECRET` | 内置默认值 | JWT 签名密钥（生产须修改） |
| `UPLOAD_PATH` | `./uploads` | 上传文件存储路径 |
| `AI_MODERATION_API_KEY` | 内置默认值 | DeepSeek AI 审核 API Key |
| `AI_MODERATION_MODEL` | `deepseek-v4-flash` | AI 审核模型 |
| `AI_MODERATION_BASE_URL` | `https://api.deepseek.com/v1` | AI 审核 API 地址 |

## 七、常用开发命令

| 操作 | 命令 | 目录 |
|------|------|------|
| 后端编译 | `mvn compile` | `backend/` |
| 后端测试 | `mvn test` | `backend/` |
| 构建镜像 | `podman build -t vibedev-backend:latest .` | `backend/` |
| 查看日志 | `podman logs -f vibedev-backend` | 任意 |
| 进入容器 | `podman exec -it vibedev-backend /bin/bash` | 任意 |
| 数据库 CLI | `podman exec -it vibedev-mysql mysql -uroot -proot vibedev` | 任意 |
| Redis CLI | `podman exec -it vibedev-redis redis-cli` | 任意 |

## 八、注意事项

1. **端口避让**：容器后端映射到 8081，前端代理也指向 8081，保持一致
2. **独立 Git 仓库**：`frontend/` 和 `backend/` 各自是独立 Git 仓库，提交需在对应目录操作
3. **邮件服务**：默认配置为示例 SMTP，需注册验证功能时请配置真实邮件服务
4. **CAS 集成**：默认 CAS 地址为示例，需 CAS 登录时请配置真实 CAS 服务
5. **Podman 构建缓存**：正常开发用 `podman build`（约 15-20 秒）；仅在 `pom.xml` 变更或缓存异常时才需 `--no-cache`
6. **CORS 配置**：Spring Security 6.x 需在 `SecurityFilterChain` 中显式添加 `.cors(Customizer.withDefaults())`，否则跨域预检请求（OPTIONS）会被拦截返回 403
7. **JPA ddl-auto**：当前配置为 `none`（由 Flyway 管理 schema），如需改回 `validate` 需确保实体定义与数据库列类型完全匹配
8. **uploads 挂载**：重建容器时必须带 `-v ./uploads:/app/uploads:Z`，否则上传文件丢失
9. **Pod 内容器启动顺序**：MySQL →（等待就绪）→ Backend。Backend 启动时 Flyway 会自动执行数据库迁移
10. **前端开发**：`cd frontend && node node_modules/vite/bin/vite.js`，访问 `http://localhost:5173`
