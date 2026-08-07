# Changelog

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
