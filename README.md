# TaskFlow Platform

## Stage 13 progress

## Stage 14 progress

Stage 14 adds regression coverage for authentication/token rejection, RBAC and data scope, task state transitions, optimistic concurrency, notification idempotency, reminder cancellation/recovery, attachment authorization, and audit consistency. An opt-in Testcontainers smoke test is available for fresh MySQL/Flyway, Redis, RabbitMQ, and MinIO infrastructure.

Stage 15 adds repeatable performance data preparation, six HTTP benchmark scenarios, protected runtime metric collection, and EXPLAIN templates. Stage 4 adds route-level frontend lazy loading and broader HTTP/HikariCP/RabbitMQ/Redis runtime evidence. A local baseline is recorded under `docs/`; numbers remain environment-specific and are not production capacity claims. The comparable post-optimization replay is currently blocked because the original administrator credential is unavailable; no substitute employee-account result is treated as a performance comparison.

Stage 16 adds backend/frontend Dockerfiles, a full six-service Compose deployment, health checks, persistent volumes, environment-driven container addresses, and safe initialization/cleanup scripts.

Stage 17 adds local Kubernetes manifests for the frontend and a two-replica backend Deployment, with ConfigMap/Secret separation, rolling updates, startup/readiness/liveness probes, resource requests/limits, and a safe local deployment script. MySQL, Redis, RabbitMQ, and MinIO remain supplied by Docker Compose for this learning phase.

Stage 18 adds a security and quality review, hardens audit source-address handling and attachment content validation, and records verified findings and unresolved production risks.

阶段 19 新增基于已验证代码整理的中文面试和简历材料，包括架构图、状态与消息时序图、RBAC 和数据范围说明、50 个项目深挖问题及参考回答、个人贡献边界，以及未经进一步验证不得使用的项目表述。

The backend now includes task draft maintenance, filtered and paginated task queries, primary/collaborator assignment, scoped detail access, batch assignee loading, task operation logs, a fixed task state machine, optimistic concurrency control using old-status-plus-version conditional updates, task comments, MinIO-backed attachment metadata workflows, persistent reminder plans, Redis ZSet scheduling, distributed scanning locks, RabbitMQ reminder publishing, idempotent notification consumers, bounded retries, dead-letter compensation, HTTP notification query APIs, and STOMP over WebSocket user-destination push. Flyway V1 through V8 are designed for a fresh MySQL database.

企业任务协同与流程管理平台，面向学习和校招面试准备，采用模块化单体架构逐阶段实现。

## 当前进度

当前阶段 19 已完成登录、Redis 会话标记、后端登出撤销、登录失败限流、前端退出体验、Token 持久化、任务与项目基础流程、评论、附件、通知中心、用户/角色/部门管理、统一错误处理、自动化测试、性能工具、全容器部署、本地 Kubernetes 应用层清单、安全质量审查和基于已验证代码的面试简历材料。阶段 1 已收紧生产 Secret、管理面和基础安全 Header；前端 Token 仍使用 Bearer + 本地存储，生产化前仍需完成 HttpOnly Cookie/CSRF 或等价的严格 XSS 防护方案。

2026-08-09 全面验收结论：项目可在本地 Compose 环境运行和演示；认证撤销、登录限流代码及测试已补齐，阶段 1 后端回归为 66 项执行、0 失败、1 项跳过，前端生产构建和安全配置静态校验通过，真实旧 Token 失效烟测通过。阶段 15 已完成固定本地数据规模下的性能基线与运行时采集，阶段 17 已在 Kind `dev` 集群完成前后端部署、探针、Pod 恢复和滚动重启验证；这些结果均不等同于生产容量或生产高可用。Token localStorage、TLS/WSS、集中式 Secret 管理、在线依赖扫描和完整浏览器 E2E 等生产基线仍不完整，项目暂不判定为生产就绪。详见[项目全面验收与高维度评估报告](docs/project-acceptance-report-2026-08-09.md)。

参考 PriceSight 项目采用的加权验收方法，本项目阶段 4 后评分建议仍为 **81/100**：本地工程基线有条件通过，可用于学习、演示和面试；生产发布不通过。阶段 4 已完成路由级懒加载、SQL/索引复核和运行时指标扩展，但同参数优化后压测因原管理员凭据不可用而未完成，因此不机械加分；OWASP/NVD 未完成、localStorage、TLS/WSS、集中式密钥管理等未完成项也不计入加分。评分明细见[结构化评分结果](docs/project-acceptance-score-2026-08-09.json)。

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

首次执行会从 `.env.example` 创建 `.env`；该模板不再放入可直接使用的密码或 JWT 值，请逐项填写本地开发 Secret 后再启动。生产环境必须使用 `SPRING_PROFILES_ACTIVE=prod` 并提供全部必需 Secret，缺失或弱默认值会 fail-fast。全容器模式包含前端、后端、MySQL、Redis、RabbitMQ、MinIO，默认前端地址为 `http://localhost:5173`。

安全边界：dev 保留本地开发便利配置；test 仅用于测试；prod 默认关闭 Swagger/OpenAPI，仅暴露 Actuator 健康探针，并要求显式的 JWT、数据库、RabbitMQ、MinIO 和 bootstrap admin Secret。当前 REST 和 STOMP 使用显式 Bearer Token，前端 Token 仍保存在 `localStorage`，因此本地 HTTP/WSS 仅适合开发和演示；生产部署还必须由可信反向代理提供 HTTPS/WSS，并在代理边界明确配置。应用默认不信任任意 `X-Forwarded-*`，只有在代理网络边界已确认时才允许调整转发 Header 策略。详见[安全与权限边界](docs/security.md)和[部署说明](docs/deployment.md)。

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
Set-Location frontend
npm run typecheck
npm run build
$env:E2E_BASE_URL = "http://127.0.0.1:5173"
$env:E2E_ADMIN_PASSWORD = "<通过安全方式注入的本地管理员密码>"
$env:E2E_TEST_USER_PASSWORD = "<本次运行专用测试密码>"
npm run e2e
$env:npm_config_registry = "https://registry.npmjs.org"
npm run audit:ci
Set-Location ..
docker compose config --quiet
F:\newinstall\kubectl.exe kustomize k8s
```

覆盖率和集成门禁：

```powershell
.\mvnw.cmd verify
.\mvnw.cmd "-Dtaskflow.integration=true" verify
```

JaCoCo 报告位于 `target/site/jacoco/`。Windows PowerShell 中必须给 `-Dtaskflow.integration=true` 加引号，避免被 Maven 误解析为 `.integration=true` 生命周期阶段。GitHub Actions 分为 `fast-check` 和 `integration-security`：前者阻断快速回归、前端构建及 Compose/Kustomize 静态错误；后者执行 Testcontainers 和覆盖率，npm/OWASP 在线扫描暂为 advisory，并明确记录网络或数据库不可用。

浏览器 E2E 使用 Playwright，覆盖登录、401/403、任务真实写入、重复提交保护、登出失效、附件入口和通知中心。测试密码只通过当前终端环境变量注入，不写入仓库；失败时 Playwright 在 `frontend/test-results/` 保留截图、视频或 trace。当前阶段的真实执行结果和 WebSocket 证据见[浏览器 E2E 验收记录](docs/e2e-browser-report-2026-08-09.md)，未通过的场景不会被包装成生产结论。

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
- [阶段 3 浏览器 E2E 验收记录](docs/e2e-browser-report-2026-08-09.md)
- [阶段 15 性能工具](docs/performance.md)
- [阶段 4 性能优化对比](docs/performance-comparison-2026-08.md)
- [阶段 16 Docker Compose 部署](docs/deployment.md)
- [阶段 17 Kubernetes 本地部署](docs/k8s-local.md)
- [阶段 18 安全和质量审查](docs/stage18-security-quality.md)
- [阶段 2 依赖安全与质量门禁记录](docs/dependency-security-report.md)
- [阶段 19 面试和简历材料](docs/stage19-interview-materials.md)
- [项目全面验收与高维度评估报告（2026-08-09）](docs/project-acceptance-report-2026-08-09.md)
- [项目级加权评分（2026-08-09）](docs/project-acceptance-score-2026-08-09.json)
- [调优基线与阶段1～7账本（2026-08-09）](docs/optimization-baseline-2026-08-09.md)
- [任务状态机](docs/task-state-machine.md)
- [消息与提醒边界](docs/message-flow.md)
- [测试说明](docs/testing.md)
- [架构说明](docs/architecture.md)
- [阶段学习笔记](docs/learning-notes.md)
- [面试笔记](docs/interview-notes.md)
