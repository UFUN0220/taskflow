# TaskFlow Platform

## Stage 13 progress

## Stage 14 progress

Stage 14 adds regression coverage for authentication/token rejection, RBAC and data scope, task state transitions, optimistic concurrency, notification idempotency, reminder cancellation/recovery, attachment authorization, and audit consistency. An opt-in Testcontainers smoke test is available for fresh MySQL/Flyway, Redis, RabbitMQ, and MinIO infrastructure.

Stage 15 adds repeatable performance data preparation, six HTTP benchmark scenarios, protected runtime metric collection, and EXPLAIN templates. Performance numbers remain environment-specific and are not prefilled.

Stage 16 adds backend/frontend Dockerfiles, a full six-service Compose deployment, health checks, persistent volumes, environment-driven container addresses, and safe initialization/cleanup scripts.

Stage 17 adds local Kubernetes manifests for the frontend and a two-replica backend Deployment, with ConfigMap/Secret separation, rolling updates, startup/readiness/liveness probes, resource requests/limits, and a safe local deployment script. MySQL, Redis, RabbitMQ, and MinIO remain supplied by Docker Compose for this learning phase.

Stage 18 adds a security and quality review, hardens audit source-address handling and attachment content validation, and records verified findings and unresolved production risks.

阶段 19 新增基于已验证代码整理的中文面试和简历材料，包括架构图、状态与消息时序图、RBAC 和数据范围说明、50 个项目深挖问题及参考回答、个人贡献边界，以及未经进一步验证不得使用的项目表述。

The backend now includes task draft maintenance, filtered and paginated task queries, primary/collaborator assignment, scoped detail access, batch assignee loading, task operation logs, a fixed task state machine, optimistic concurrency control using old-status-plus-version conditional updates, task comments, MinIO-backed attachment metadata workflows, persistent reminder plans, Redis ZSet scheduling, distributed scanning locks, RabbitMQ reminder publishing, idempotent notification consumers, bounded retries, dead-letter compensation, HTTP notification query APIs, and STOMP over WebSocket user-destination push. Flyway V1 through V8 are designed for a fresh MySQL database.

企业任务协同与流程管理平台，面向学习和校招面试准备，采用模块化单体架构逐阶段实现。

## 当前进度

当前阶段 19 已完成登录、Redis 会话标记、后端登出撤销、登录失败限流、前端退出体验、Token 持久化、任务与项目基础流程、评论、附件、通知中心、用户/角色/部门管理、统一错误处理、自动化测试、性能工具、全容器部署、本地 Kubernetes 应用层清单、安全质量审查和基于已验证代码的面试简历材料。前端 Token 仍使用本地存储，生产化前需继续评估 HttpOnly Cookie、CSRF 和 CSP。

2026-08-09 全面验收结论：项目可在本地 Compose 环境运行和演示；认证撤销、登录限流代码及测试已补齐，默认后端回归为 64 项通过，显式阶段 14 Testcontainers 基础设施测试通过，真实旧 Token 失效和限流烟测通过。Kubernetes 实机证据、Token 存储生产化和密钥/TLS 基线仍不完整，项目暂不判定为生产就绪。详见[项目全面验收与高维度评估报告](docs/project-acceptance-report-2026-08-09.md)。

## 技术栈

- 后端：Java 17、Spring Boot 3.4.x、Spring Security、Maven Wrapper；
- 前端：React、TypeScript、Vite、React Router、Axios、Ant Design；
- 基础设施：MySQL 8、Redis 7、RabbitMQ 3、MinIO；
- 运行方式：Windows 11、Docker Desktop、Docker Compose；阶段 17 增加 Kubernetes 本地应用层部署。

## 目录

```text
backend/
├─ src/main/java/yvon/backend/       # Spring Boot 后端
├─ src/test/                         # 后端测试
├─ frontend/                         # React + Vite 前端
├─ docker-compose.yml                 # 阶段 16 全容器部署
├─ k8s/                               # 阶段 17 Kubernetes 应用层清单
├─ .env.example                       # 本地环境变量模板
└─ docs/                              # 持续维护的项目文档
```

## 启动全容器模式

在仓库根目录执行：

```powershell
.\scripts\init-compose.ps1 -Rebuild
```

首次执行会从 `.env.example` 创建 `.env`，请修改本地密码和 `TASKFLOW_JWT_SECRET`。全容器模式包含前端、后端、MySQL、Redis、RabbitMQ、MinIO，默认前端地址为 `http://localhost:5173`。

容器内部使用服务名连接：后端连接 `mysql:3306`、`redis:6379`、`rabbitmq:5672`、`minio:9000`；前端 Nginx 代理到 `backend:8080`。宿主机端口默认映射为 MySQL `3307`、Redis `6380`、RabbitMQ `5673`、管理台 `15673`、MinIO `9000/9001`、后端 `8080`、前端 `5173`。

停止但保留数据卷：

```powershell
.\scripts\cleanup-compose.ps1
```

删除卷必须显式确认：

```powershell
.\scripts\cleanup-compose.ps1 -RemoveVolumes -ConfirmDataLoss
```

## 本地开发模式

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

另开终端运行前端：

```powershell
Set-Location frontend
npm run dev
```

本地模式下 Vite 将 `/api` 和 `/ws` 代理到宿主机 `localhost:8080`。仅启动基础设施时可执行 `docker compose up -d mysql redis rabbitmq minio`。

本项目工具缓存统一放在 F 盘：Maven 用户目录为 `F:\newinstall\maven-user`、本地仓库为 `F:\newinstall\maven-repository`，Gradle 用户目录为 `F:\newinstall\gradle-user-home`，npm 缓存为 `F:\newinstall\npm-cache`。项目 `.mvn/maven.config` 会把 Maven 本地仓库固定到 F 盘；新的 PowerShell 或 IDE 会话通过 `MAVEN_USER_HOME`、`MAVEN_ARGS`、`GRADLE_USER_HOME`、`NPM_CONFIG_CACHE` 和 `JAVA_HOME` 使用这些目录，迁移后请重新打开终端或 IDE。

Testcontainers 配置位于项目内 `src/test/resources/testcontainers.properties`，不依赖用户目录下的 C 盘配置；Docker Desktop 本体配置仍由 Docker 管理，未迁移其必要的 `.docker` 用户目录。

健康检查：<http://localhost:8080/api/health> 或 <http://localhost:8080/actuator/health>。

## 执行数据库迁移

先启动 MySQL 并准备本地配置，再显式开启 Flyway：

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3307/taskflow?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME = "taskflow"
$env:DB_PASSWORD = "change-me-in-local-config"
$env:FLYWAY_ENABLED = "true"
.\mvnw.cmd spring-boot:run
```

迁移脚本位于 `src/main/resources/db/migration/`，阶段10新增 `V8__add_notification_delivery_and_permissions.sql`，为通知幂等和死信补偿增加结构与权限。附件和提醒默认关闭；启用提醒时需配置 Redis、RabbitMQ。无数据库时默认关闭 Flyway，便于运行基础测试。

## 启动前端

```powershell
Set-Location frontend
npm install
npm run dev
```

前端地址：<http://localhost:5173>。`/api` 请求在开发服务器中代理到后端 `8080` 端口。

## 验证命令

```powershell
.\mvnw.cmd test
Set-Location frontend; npm run build
Set-Location ..; docker compose config --quiet
```

## 文档

- [完整 Codex 分阶段开发 Prompt](docs/codex-phased-development-prompt.md)
- [阶段 0 部署说明](docs/deployment.md)
- [需求与角色](docs/requirements.md)
- [数据库设计](docs/database.md)
- [安全与数据范围](docs/security.md)
- [阶段 4 角色与组织管理](docs/stage4-management.md)
- [阶段 5 项目与任务管理](docs/stage5-project-task.md)
- [阶段 6 任务查询与分配](docs/stage6-task-query.md)
- [阶段 7 状态机与并发控制](docs/stage7-concurrency.md)
- [阶段 8 评论与附件](docs/stage8-comments-attachments.md)
- [阶段 9 提醒计划](docs/stage9-reminders.md)
- [阶段 10 RabbitMQ 异步通知](docs/stage10-notifications.md)
- [阶段 11 WebSocket 通知中心](docs/stage11-websocket-notifications.md)
- [阶段 12 审计日志和可观测性](docs/stage12-audit-observability.md)
- [阶段 13 前端基础功能](docs/stage13-frontend.md)
- [阶段 14 自动化测试](docs/stage14-testing.md)
- [阶段 15 性能工具](docs/performance.md)
- [阶段 16 Docker Compose 部署](docs/deployment.md)
- [阶段 17 Kubernetes 本地部署](docs/k8s-local.md)
- [阶段 18 安全和质量审查](docs/stage18-security-quality.md)
- [阶段 19 面试和简历材料](docs/stage19-interview-materials.md)
- [项目全面验收与高维度评估报告（2026-08-09）](docs/project-acceptance-report-2026-08-09.md)
- [任务状态机](docs/task-state-machine.md)
- [消息与提醒边界](docs/message-flow.md)
- [测试说明](docs/testing.md)
- [架构说明](docs/architecture.md)
- [阶段学习笔记](docs/learning-notes.md)
- [面试笔记](docs/interview-notes.md)
