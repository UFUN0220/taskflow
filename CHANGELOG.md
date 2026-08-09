# Changelog

## 阶段 6 Kind 生产样式本地验证 - 2026-08-09

- 新增 `k8s/base` 与 `k8s/overlays/kind-production-like` 配置分层；overlay 使用 prod profile、只暴露 health 管理面，并且不覆盖已注入的 `taskflow-secret`。
- 新增 `taskflow-edge` 的 `/`、`/api`、`/ws` TLS 路由定义和 `scripts/prepare-kind-tls.ps1`；本地私钥保存在被忽略的 `runtime-secrets`，不提交仓库。
- 当前 Kind 没有 Ingress Controller，因此增加 namespace 内 `taskflow-local-edge` 仅用于本地 HTTPS/WSS 验证；HTTPS 首页和 `/api/health` 返回 200，WSS Upgrade 返回 101。
- 重新验证 backend 2 副本、frontend 1 副本、local edge rollout 和健康探针；不把 Kind 单节点结果包装成生产 HA。STOMP 认证/订阅/浏览器通知闭环仍未在本阶段通过。
- 详细证据见 `docs/kind-production-like-validation.md`，项目评分保持 82/100，生产就绪仍为否。

## 阶段 5 故障注入与恢复 - 2026-08-09

- 新增 `scripts/fault-injection.ps1`，默认计划模式，只允许对精确的 TaskFlow 容器执行 stop/start，禁止删除卷、清库和 `compose down`。
- Redis 短暂不可用真实演练通过：会话/Redis 索引期间不可用，恢复后恢复，Flyway V8 和核心计数不变。
- RabbitMQ 短暂停止/恢复真实演练通过；真实非法消息到 retry/DLQ/replay 因执行审批未完成，不包装为已通过。
- MinIO 停止期间附件上传真实返回 500，附件元数据进入 FAILED，MinIO 恢复后健康；对象列表扫描未完成。
- MySQL 停止、backend 重启和浏览器 WebSocket 重连本阶段未复验，项目评分按证据边界调整为 82/100，生产 HA 仍不通过。

## 阶段 4 性能与可观测性调优 - 2026-08-09

- 为 Dashboard、Login、Management 和 Task 路由增加懒加载；前端构建通过，旧入口 JS 1,156,965 bytes，新最大共享 chunk 747,991 bytes，下降约 35.38%，但仍保留 Vite 500 KB 提示，未声称总传输量下降。
- 复核任务列表、通知列表 SQL、批量负责人查询和现有索引；当前 EXPLAIN 不足以证明需要新增索引，因此没有添加 Flyway migration。
- 扩展运行时采集至 HTTP、HikariCP、Executor、JVM、RabbitMQ 和 Redis，并新增阶段4运行时、EXPLAIN 和对比报告。
- 同参数优化后压测因原管理员凭据不可用而阻塞；没有使用员工账号制造不可比的 QPS/p95/p99 结论，项目评分保持 81/100。

## 阶段 3 浏览器 E2E - 2026-08-09

- 新增 Playwright 9 场景、运行编号隔离夹具、失败截图/视频/trace 保留和真实 STOMP 浏览器观测；修正登录完成等待、Modal 提交定位、标题隔离，并让 WebSocket 后续 STOMP 帧恢复 CONNECT 认证 Principal。当前 Compose 浏览器实测为 4/9 通过，真实订阅仍待修复后复验，详见 `docs/e2e-browser-report-2026-08-09.md`。

## 认证遗留问题修复 - 2026-08-09

- 2026-08-09 阶段 2 工程治理：新增 JaCoCo HTML/XML 报告和 bundle/核心类覆盖率门禁，新增 fast-check 与 integration-security GitHub Actions，纳入 Testcontainers、Compose/Kustomize 静态校验、npm audit 和 OWASP Dependency-Check；真实扫描结果与网络/NVD 超时边界记录在 `docs/dependency-security-report.md`。
- 2026-08-09 阶段 1 安全基线收敛：按 dev/test/prod 分离敏感配置，生产缺失/弱 Secret fail-fast，收紧 prod Actuator/OpenAPI，增加 Spring Security/Nginx 基础安全 Header，明确不信任任意转发 Header，并将 `.env.example`/Kubernetes Secret 模板改为空值安全模板；保留 Bearer + STOMP 一致性，localStorage、TLS/WSS 和集中式密钥管理仍列为生产遗留风险。
- 参考 PriceSight 项目级加权验收范式，新增 TaskFlow 100 分制综合评估：79/100；本地工程基线有条件通过，生产发布不通过。评分维度覆盖需求核心、架构、安全、测试、评估可信度、运行集成、工程治理和文档交付。
- 新增 `docs/project-acceptance-score-2026-08-09.json` 机器可读评分结果，并将 P0/P1/P2 生产门禁、权重、证据和分层交付判定同步到全面验收报告。
- 阶段 0 冻结 79/100 调优基线：记录 Git HEAD、dirty 工作区、F 盘工具路径、端口/环境变量、Flyway V1–V8、测试和部署入口；新增阶段 1～7 调优待办矩阵。基线阶段未修改业务代码、数据库卷或 Kind 资源。
- JWT 增加唯一 `jti`，登录后在 Redis 保存带过期时间的活动会话标记。
- 新增后端 `POST /api/auth/logout`，HTTP 鉴权过滤器和 WebSocket CONNECT 均检查 Redis 会话，主动退出后旧会话被拒绝。
- 新增按登录名和来源地址哈希分组的 Redis 登录失败窗口，默认 10 次/60 秒，并增加统一 429 错误码。
- 前端退出流程先调用后端撤销接口，再清理本地 Token；服务端未确认时向用户提示。
- 新增会话、登出、限流和 JJWT jti 测试；默认回归为 64 项执行、0 失败，显式阶段 14 Testcontainers 测试另行通过，真实 Compose 旧 Token 失效和限流烟测通过。
- Testcontainers 1.21.4 已在当前 Docker Engine 29.6.2/API 1.55 环境完成 MySQL、Redis、RabbitMQ、MinIO 和 Flyway 集成烟测。
- 将 Gradle、npm、Maven 及 Testcontainers 的项目相关配置/缓存固定到 F 盘，避免项目工具继续在 C 盘生成非必要缓存。
- 补充本地 Kind `dev` 集群环境信息：Kubernetes v1.32.2、单节点 `dev-control-plane`、context `kind-dev`，状态 Ready；kind v0.27.0 位于 F 盘。另记录 kubectl 当前由 Docker Desktop 提供 v1.36.1，F 盘副本为 v1.32.0。
- 完成阶段 17 Kind 实机验收：后端 2 副本、前端 1 副本通过探针，前后端端口转发健康检查返回 HTTP 200；后端 Pod 删除后自动恢复，前后端滚动重启成功。该结果仅覆盖本地单节点应用层。
- 完成阶段 15 本地性能基线：固定 10 个部门、100 个用户、1000 个任务，20 并发、预热 10 秒、采样 60 秒；六个场景均 0 错误，报告写入 `docs/performance-baseline.json`，运行时指标写入 `docs/performance-runtime.json`，EXPLAIN 输出写入 `docs/performance-explain.txt`。数字仅代表当前 Windows 本机，不作为生产容量承诺。
- 将 Testcontainers 从 1.20.6 升级到 1.21.4，针对 Docker Engine 29.6.2/API 1.55 的兼容性问题进行环境修复。

## 项目全面验收 - 2026-08-09

- 新增项目全面验收与高维度评估报告，区分构建通过、功能通过、运行态证据、生产风险和未验证项。
- 重新执行后端回归测试：58 项执行、0 失败、1 项跳过；前端生产构建和 Compose 配置通过。
- 通过现有 Compose 完成六服务健康、核心认证/API、Flyway V8、重启保卷恢复烟测；未删除数据库或 Docker 卷。
- 初版报告曾记录阶段 3 的 Redis 会话、后端登出和 Token 主动撤销缺口；该缺口已由后续“认证遗留问题修复”条目补齐代码路径。
- 明确记录阶段 14 专用 Testcontainers 测试已通过，以及阶段 17 Kind 应用层实机验收和阶段 15 本地性能基线的证据边界。
- 同步修正认证、自动化测试和部署文档中的证据边界与当前验证数字。

## 阶段 19 - 面试和简历材料 - 2026-08-08

- 新增基于已验证代码整理的中文简历描述、30 秒和 2 分钟项目介绍，以及实习场景下的个人职责边界。
- 新增架构图、任务状态图、RBAC/数据范围图和提醒消息链路图。
- 新增 50 个项目深挖问题及参考回答、5 个困难问题、可如实量化指标和禁止/未验证表述清单。
- 明确区分个人贡献、团队协作成果和项目整体成果。

## Stage 18 - Security and quality review - 2026-08-08

- Hardened audit logging to avoid treating client-controlled `X-Forwarded-For` as authoritative.
- Added attachment content-signature checks for PDF, PNG, JPEG and NUL-byte rejection for text files.
- Added regression coverage for spoofed forwarded addresses and mismatched binary attachment content.
- Removed a copyable administrator password from authentication documentation.
- Documented unresolved production risks including default local secrets, localStorage token exposure, missing login rate limiting, and single-instance local infrastructure.

## Stage 17 - Local Kubernetes deployment - 2026-08-08

- Added Namespace, ConfigMap, Secret, frontend/backend Deployments, and ClusterIP Services under `k8s/`.
- Configured two backend replicas, RollingUpdate strategy, startup/readiness/liveness probes, and resource requests/limits.
- Added a PowerShell renderer/deployer with optional Kind and Minikube local-image loading.
- Documented the local learning topology where Kubernetes hosts the application layer and Docker Compose continues to provide middleware.
- Initial implementation did not claim live Pod recovery validation; the follow-up local Kind `dev` verification completed deployment, probes, Pod recovery, and rolling restart checks.

## Stage 16 - Docker Compose deployment - 2026-08-07

- Added multi-stage Java 17 backend and Node/Nginx frontend Dockerfiles.
- Extended Compose from infrastructure-only to a six-service application deployment with internal service addresses, health checks, restart policies, environment-driven ports and credentials, and persistent named volumes.
- Added safe PowerShell initialization and cleanup scripts; volume removal requires explicit confirmation flags.
- Documented local development mode versus full-container mode.

## Stage 15 - Query optimization and performance harness - 2026-08-07

- Added standard-library Python tooling for configurable department/user/task data preparation and login, task list, task detail, task creation, state update, and notification list benchmark scenarios.
- Added EXPLAIN statements and a PowerShell runtime collector for Actuator, JVM, and Redis observations.
- Exposed authenticated Actuator metrics for connection-pool, executor, JVM, and GC inspection.
- Added a reproducible local baseline and runtime/EXPLAIN evidence without changing indexes based only on one machine's result; production capacity remains unverified.

## Stage 14 - Automated testing - 2026-08-07

- Added expired/malformed JWT and invalid-credential authentication regression tests.
- Added an opt-in Testcontainers smoke test covering fresh Flyway migration on MySQL and availability of Redis, RabbitMQ, and MinIO.
- Documented the existing regression categories for RBAC, data scope, state transitions, optimistic concurrency, notification idempotency, reminder recovery/cancellation, attachment authorization, and audit consistency.
- Kept the default Maven test command Docker-independent; container tests require `-Dtaskflow.integration=true`.

## Stage 13 - Frontend foundation - 2026-08-07

- Added React login/logout, persisted access tokens, current-user loading, automatic 401 sign-out, and unified API error handling.
- Added project dashboard, task list/detail/create/edit/status/assignment flows, comments, attachments, notification center integration, and duplicate-submit protection.
- Added permission-aware user, role, and department management pages.

## Stage 12 - Audit logs and observability - 2026-08-07

- Added `AuditAction` AOP auditing for login, permission/user changes, task key operations, attachment deletion, and notification dead-letter replay.
- Added protected audit-log queries with `audit:view`, safe metadata-only details, trace/operator/resource/result fields, slow-request WARN logging, RabbitMQ messageId MDC correlation, and Actuator health probes.
- Added stage 12 unit coverage for successful/failed audit recording and sensitive-value exclusion.

## Stage 11 - WebSocket notification center - 2026-08-07

### Added

- Added STOMP over WebSocket endpoint `/ws/notifications` with JWT authentication in the STOMP `CONNECT` frame.
- Added server-derived user destinations, multi-device session tracking, and disconnect cleanup.
- Added after-commit notification push events with failure isolation from notification/task transactions.
- Added a lightweight frontend notification center with HTTP unread backfill, read/all-read actions, bounded reconnects, and Vite WebSocket proxying.
- Added WebSocket authentication and push-target unit tests.

### Fixed

- Compare JWT numeric `uid` by value instead of Java wrapper type, so valid small user IDs parsed as `Integer` authenticate correctly against database `Long` IDs.
- Wrap immutable STOMP headers and return a new authenticated message so `CONNECT` authentication works with Spring's immutable message headers.

### Not included

- The full frontend login workflow remains a later stage; the notification center reads an existing access token from local storage.

## Documentation - complete phased development prompt - 2026-08-07

### Added

- Archived the complete user-provided Codex phased development prompt at `docs/codex-phased-development-prompt.md`.
- Added the prompt to the README documentation index for future stage planning and acceptance checks.

## Stage 10 - RabbitMQ asynchronous notifications - 2026-08-07

### Added

- Added V8 notification source-message idempotency, dead-letter records, and notification/dead-letter permissions.
- Added reminder and task-status event messages with event type, message ID, trace ID propagation, and after-commit task-status publishing.
- Added manual-Ack notification consumer, bounded TTL retry queue, dead-letter queue, and explicit failure-reason recording.
- Added idempotent notification creation for task assignees and task-status recipients.
- Added current-user notification pagination, unread count, mark-read, mark-all-read, dead-letter viewing, and administrator replay APIs.
- Added publisher confirm, mandatory return, and correlation-data configuration for RabbitMQ producers.

### Not included

- WebSocket real-time notification delivery remains in stage 11.

## Stage 9 - reminder plans - 2026-08-07

### Added

- Added persistent due-soon and overdue reminder plan orchestration using the existing `reminder_plan` table.
- Added deadline and terminal-task synchronization, including cancellation of stale plans after deadline changes.
- Added Redis ZSet indexing, low-frequency database rebuild, and a TTL-based distributed scan lock.
- Added RabbitMQ exchange/queue declaration and stable plan-id message publishing; consumers remain in stage 10.
- Added version-conditional emitted/failed state transitions and reminder scheduling tests.

## Stage 8 - comments and attachments - 2026-08-07

### Added

- Added V7 stable comment and attachment permissions without changing the existing V1 tables.
- Added task comment create/page APIs and backend system-event comment support.
- Added MinIO-backed attachment upload, metadata page, streaming download, presigned URL, and delete APIs.
- Added file size/type/extension validation, filename sanitization, unpredictable object keys, SHA-256 metadata, task-scope checks, and conditional attachment status transitions.
- Added upload/delete compensation paths for MySQL and MinIO failures; no unbounded retry loop is introduced.
- Added stage 8 documentation and unit tests for permissions, validation, path isolation, and storage failure handling.

## Stage 7 - task state machine and concurrency - 2026-08-07

### Added

- Added old-status-plus-version conditional updates for task commands to prevent concurrent state overwrites.
- Made transfer consume a task version and keep assignment changes and operation logs in one transaction.
- Added a `complete` compatibility endpoint, explicit `COMMON_409` duplicate/concurrency semantics, and state concurrency tests.
- Added stage 7 state-machine and concurrency documentation.

## Stage 6 - task creation, query, and assignment - 2026-08-07

### Added

- Added V6 `task:update` and `task:delete` permissions for draft maintenance.
- Added draft edit and logical-delete APIs with creator/permission checks and optimistic locking.
- Added title, status, priority, assignee, creator, department, project, due-time, and creation-time filters.
- Reworked paginated task responses to batch-load all assignees in one query and avoid page-level N+1 queries.
- Added stage6 migration and task service tests for draft rules and batch loading.

### Verified

- Full backend test suite passed with 23 tests and no failures.
- MySQL 8.4 applied Flyway V6 successfully; representative task queries used the existing creator, project, and assignee indexes in `EXPLAIN`.

## Stage 5 - project and task lifecycle - 2026-08-07

### Added

- Added V5 project/task permissions and built-in role assignments.
- Added project creation, scoped project listing, and project member management APIs.
- Added task creation, scoped pagination/detail queries, primary/collaborator assignment, and transfer APIs.
- Added explicit task lifecycle commands: submit, accept, submit review, approve, reject, start, cancel, and archive.
- Added task state-machine tests, version-based optimistic locking, and transactional operation logs with before/after status and Trace ID.

### Not included

- Comments, attachments, notifications, reminder scheduling, MQ consumers, and MinIO integration remain outside this stage.

## Stage 4 - role, organization, and data-scope management - 2026-08-07

### Added

- Added V4 management permissions, built-in manager roles, and role data-scope seeds.
- Added role list/create/update APIs with stable permission-code validation and transactional relation replacement.
- Added department create/update APIs and user create/status/role-assignment APIs with Jakarta Validation and optimistic locking.
- Applied `SELF`, `DEPARTMENT`, `DEPARTMENT_AND_CHILDREN`, and `ALL` scope resolution to the user pagination query; project scope remains reserved for project/task queries.
- Added stage4 migration and data-scope unit tests.

### Not included

- Task/project business services, project data-scope query adapters, MQ consumers, and file storage remain outside this stage.

## Stage 3 - authentication and authorization foundations - 2026-08-06

### Added

- JWT login with BCrypt password verification and stateless Spring Security filtering.
- Current-user endpoint, active user/role/permission loading, and backend `@PreAuthorize` checks.
- User pagination and active department tree read APIs.
- Flyway V3 stable permission codes, built-in roles, and role-permission assignments.
- Environment-controlled, idempotent development administrator bootstrap without plaintext password seeds.
- Authentication, JWT, controller, service, and V3 migration tests.

### Verified

- Fresh MySQL database reached schema version 3.
- Local administrator login returned HTTP 200 and a JWT.

## Stage 2 - backend foundations - 2026-08-06

### Added

- Unified `ApiResponse`, stable `BusinessErrorCode`, `BusinessException`, and `GlobalExceptionHandler`.
- Trace ID filter/context with MDC and response-header propagation.
- MyBatis-Plus pagination, logical deletion, optimistic locking, automatic timestamps, and base audit fields.
- Optional Springdoc OpenAPI configuration.
- Controller, service, and migration-structure tests for the stage acceptance criteria.

### Verified

- Fresh MySQL 8.4 database applied Flyway V1 and V2 successfully and reached schema version 2; the backend health endpoint returned HTTP 200 with a Trace ID.

## [阶段 1] - 2026-08-06

### Added

- 增加 Flyway V1 初始化迁移，创建组织、权限、项目、任务、评论、附件、通知、提醒和审计表；
- 增加唯一约束、外键、状态检查约束、乐观锁 `version` 字段和基于查询场景的联合索引；
- 增加数据库结构静态测试；
- 补充角色边界、数据范围、任务状态机、ER 图和消息/提醒边界文档。

### Not included

- 未实现登录、Token、RBAC 接口、数据权限查询拦截、任务应用服务和 MQ 消费者。
- 真实 MySQL 空库迁移已在 MySQL 8.4 容器中验证通过；初次验证期间记录的 Docker 镜像下载问题已恢复。

## [阶段 0] - 2026-08-06

### Added

- 建立 Spring Boot 3.4.8 后端基础工程和 `/api/health` 健康检查接口；
- 建立 React + TypeScript + Vite + Ant Design 前端基础页面；
- 增加 MySQL、Redis、RabbitMQ、MinIO 的 Docker Compose 开发基础设施及健康检查；
- 增加 `.env.example`、README 和阶段 0 文档；
- 修正 Maven Wrapper 在 Windows 普通用户目录下的兼容性问题。
- 将 Maven 用户配置、Wrapper 发行版和本地依赖仓库迁移到 `F:\projects_2027\taskflow-platform\maven-user`，并更新当前用户的 Maven/Java 环境变量。

### Not included

- 未实现数据库表、Flyway 迁移、用户认证、RBAC、任务业务和消息业务。
