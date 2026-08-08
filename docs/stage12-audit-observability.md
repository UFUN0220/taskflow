# 阶段 12：审计日志和可观测性

阶段 12 在已有 `task_operation_log` 和 `audit_log` 表基础上补齐关键操作追踪与基础运行观测。两类日志职责保持分离：任务状态前后值由业务代码显式写入 `task_operation_log`；跨模块的访问、管理和补偿操作由 `AuditAction` AOP 写入 `audit_log`。

## 审计范围

以下操作通过 `@AuditAction` 统一记录成功和失败结果：登录、用户创建/状态修改/角色分配、角色创建/修改、任务关键操作、附件删除和通知死信重放。

审计记录包含 `traceId`、操作者 ID、资源类型/ID、动作、结果、HTTP 方法、URI、来源地址和发生时间。普通用户不能访问审计查询接口，查询接口要求 `audit:view` 权限；审计表没有普通修改/删除接口。

## 安全与脱敏

审计切面只写入方法、耗时和异常类型等安全元数据，不序列化请求体，因此不会记录密码、完整 Token、附件内容或其他敏感参数。异常日志同样只保留可定位所需的 traceId、消息 ID和异常类型。

## 可观测性

- `TraceIdFilter` 接受安全格式的 `X-Trace-Id`，并在响应和 MDC 中贯穿请求；
- `SlowRequestLoggingFilter` 默认将耗时达到 1000ms 的请求记录为 WARN，可通过 `SLOW_REQUEST_THRESHOLD_MS` 调整；
- RabbitMQ 发布和消费链路保留 messageId，并将消费中的 messageId/traceId 放入 MDC；
- Actuator 暴露 `/actuator/health`，启用基础 liveness/readiness probes，健康详情默认不对外展示。

审计落库默认由 `AUDIT_ENABLED` 控制。使用本地 MySQL/Flyway 配置时应打开 `taskflow.audit.enabled=true`；无数据库的单元测试环境保持关闭。

代码级测试覆盖审计成功/失败、trace 关联和敏感信息不写入。真实数据库中的审计落库、权限过滤和 Actuator/RabbitMQ 运行态，需要在本地 Compose 基础设施启动后额外执行集成验证。
