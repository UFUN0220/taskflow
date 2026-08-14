# Stage 11.5B-E2E：Spring Boot 3.5.16 升级后浏览器回归

> **P1 当前复验覆盖说明（2026-08-14）**：本文前述 `10/10`、`2×9/9` 和 `85/100` 是历史阶段证据，不是本次独立最终结论。P1 最终复验的当前结果为 direct 定向 `20/20`、proxy 定向 `20/20`，direct 完整 `2×19/19`，proxy 完整 `2×19/19`，正式评分保持 `83/100`。C1/C2/C3/C5/C6/C7 已有当前证据；服务器物理 WebSocket `sendMessage` 边界 C4 因 acceptance 镜像未重新部署诊断类而为 `NOT_ESTABLISHED`。详见 [P1 最终复验报告](p1-nginx-stomp-proxy-stability-2026-08-14.md)。

## 结论

本报告保留了升级后首轮 `8/9 → 9/9` 的历史证据，并补充 Stage 11.5B-E2E-F 的定向诊断和稳定性门禁。新的 acceptance-only 诊断在真实 Chromium 中关联了持久化、user destination dispatch、client outbound channel、浏览器 MESSAGE 和 UI 应用五个检查点。

因此本批次保持：

- `Stage 11.5B = COMPLETED`
- Candidate = `COMPLETED`
- 评分保持 `85/100`
- Stage 13 已满足前置条件，但本轮按要求不启动

## 安全变量边界

本次变量仅在 PowerShell 子进程中随机生成，未写入文件、日志、Git 或 artifact。变量名如下，未记录任何值：

```text
TASKFLOW_ACCEPTANCE_DB_ROOT_PASSWORD
TASKFLOW_ACCEPTANCE_DB_USERNAME
TASKFLOW_ACCEPTANCE_DB_PASSWORD
TASKFLOW_ACCEPTANCE_RABBITMQ_USERNAME
TASKFLOW_ACCEPTANCE_RABBITMQ_PASSWORD
TASKFLOW_ACCEPTANCE_MINIO_ROOT_USER
TASKFLOW_ACCEPTANCE_MINIO_ROOT_PASSWORD
TASKFLOW_ACCEPTANCE_JWT_SECRET
TASKFLOW_ACCEPTANCE_ADMIN_USERNAME
TASKFLOW_ACCEPTANCE_ADMIN_PASSWORD
TASKFLOW_ACCEPTANCE_TEST_USER_PASSWORD
TASKFLOW_ACCEPTANCE_BASE_URL
TASKFLOW_ACCEPTANCE_RUN_ID
TASKFLOW_ACCEPTANCE_ADMIN_EMPLOYEE_NO
TASKFLOW_ACCEPTANCE_ADMIN_DISPLAY_NAME
TASKFLOW_ACCEPTANCE_FRONTEND_PORT
TASKFLOW_ACCEPTANCE_BACKEND_PORT
TASKFLOW_E2E_DIRECT_WS
TASKFLOW_E2E_DIRECT_WS_PORT
```

## Acceptance 健康证据

通过隔离 acceptance Compose 环境、容器健康检查和真实 Chromium 浏览器 smoke 验证：

- MySQL、Redis、RabbitMQ、MinIO healthy；
- 后端 healthy，Flyway 新库迁移到 V8；
- 管理员登录成功；
- HttpOnly/SameSite Cookie 设置成功；
- `/api/auth/me`、任务列表成功；
- CSRF bootstrap 和 logout 成功；
- logout 后旧会话返回 401。

`scripts/acceptance-check.ps1` 已修复 HTTP 4xx 响应读取兼容性，并在 Windows PowerShell 5.1 与 PowerShell 7.x 下分别通过：health、登录、Cookie/CSRF、任务列表、logout 及旧会话 401。预期旧会话 401 被标记为 `EXPECTED_HTTP_FAILURE`，其他未预期 4xx 仍会阻断脚本。

## 浏览器结果

每轮均为 9 个场景、单 worker、Chromium、CI retry 未用于掩盖本地结果：

| 路径 | Run 1 | Run 2 | 真实业务 STOMP MESSAGE | 409 fixture |
|---|---:|---:|---|---|
| Backend direct WebSocket | 8/9 | 9/9 | Run 1 FAIL，Run 2 PASS | PASS |
| Frontend Nginx `/ws` proxy | 8/9 | 9/9 | Run 1 FAIL，Run 2 PASS | PASS |

成功的 8 个场景包括登录、401、403、任务读写、重复提交、登出旧会话失效、断线后 HTTP 补拉和附件上传。失败场景始终是“浏览器真实收到 STOMP 通知消息”。

失败证据保存在本地忽略目录：

```text
frontend/test-results/taskflow-浏览器真实收到-STOMP-通知消息/test-failed-1.png
frontend/test-results/taskflow-浏览器真实收到-STOMP-通知消息/video.webm
frontend/test-results/taskflow-浏览器真实收到-STOMP-通知消息/trace.zip
frontend/test-results/taskflow-浏览器真实收到-STOMP-通知消息/error-context.md
```

失败帧证据明确显示：

```text
CONNECTED user-name:1
SUBSCRIBE /user/queue/notifications
SUBSCRIPTION_READY MESSAGE
```

同时 REST `/api/notifications` 已存在包含本轮任务号的未读通知；但在 15 秒条件等待窗口内没有收到包含该任务号的业务 `MESSAGE`。第二轮相同场景收到业务 MESSAGE 并通过页面断言。

## Root cause boundary

当前证据不支持把问题归因于 Nginx URI rewrite、Origin、Cookie、Principal 或 Spring Boot 3.5.16 编译兼容性：

- direct 和 proxy 都能 CONNECTED、SUBSCRIBE、获得 `SUBSCRIPTION_READY`；
- CONNECTED 的 Principal 均为用户 `1`；
- proxy Run 2 能完成真实业务 MESSAGE；
- Maven/Testcontainers/CI 仍通过，后端消息和通知落库成功。

更准确的未关闭问题是：通知事实落库与 WebSocket push 之间没有端到端确认或可靠 replay 机制；在某些时序下 `NotificationPushService` 的一次性 user-destination push 未被浏览器观察到，后续 HTTP 补拉可以恢复事实，但不能证明实时 MESSAGE 已到达。当前新增的 E2E 条件等待只保证收到真实 `SUBSCRIPTION_READY`，没有掩盖该未解决的投递偶发性。

## 其他回归证据

- 本地 `mvnw.cmd -DskipTests package`：通过，当前提交重新打包成功；
- 远程 Testcontainers：84 tests、0 failures、0 errors、0 skipped，Stage12 4/4；
- 远程 fast-check：通过；
- 远程 OSV：0 affected / 0 vulnerabilities；
- npm audit：0 vulnerabilities；
- 前端 typecheck：通过；acceptance 镜像构建：通过。

## 未关闭项

1. 本地 Docker Compose 单节点仍不等于生产级 WebSocket HA、跨实例广播或 broker 高可用。
2. WebSocket 仍是 best-effort push；通知事实由 MySQL/REST 补拉承载，不能宣称 WebSocket 自身提供持久投递保证。
3. 评分尚未因本阶段自动上调，需后续独立验收时重新评分。

## Flake Root Cause Analysis

### 复现与分类

历史升级后回归中，direct 和 proxy 各有首轮 `8/9`、第二轮 `9/9`；失败均为同一通知场景。Stage 11.5B-E2E-F 使用同一 acceptance 数据边界和真实 Chromium 完成：

| 路径 | 定向通知 | 完整 9 场景 Run 1 | 完整 9 场景 Run 2 |
|---|---:|---:|---:|
| Backend direct | 10/10 | 9/9 | 9/9 |
| Nginx `/ws` proxy | 10/10 | 9/9 | 9/9 |

未修改正式通知发送语义，也没有重复触发同一业务事件、固定 sleep、无限 retry 或弱化 MESSAGE 断言。定向测试在 READY 后只触发一次业务事件，按精确 `notificationId` 等待浏览器 MESSAGE，并为每次新建浏览器 context/WebSocket。

综合证据分类为：`TEST_OBSERVATION_RACE / SERVER_LOSS_NOT_REPRODUCED`。原失败更符合“未按精确通知事实关联并观察完整时序”的测试观测竞态；没有捕获到 C1/C2 存在而 C3 缺失的服务器投递失败样本，因此不能把问题归因于 Nginx、Principal、user destination 或 broker 丢消息。这个结论不包装成生产可靠投递结论。

### 单条 correlation 证据

代表性代理路径样本 `notificationId=23`（值仅为本地 acceptance 数据）：

| Checkpoint | 实际证据 |
|---|---|
| C1 PERSISTED | `notificationId=23`、`sourceMessageId=task:23:1`，MySQL 事实已提交 |
| C2 DISPATCH_REQUESTED | `/queue/notifications`；`sessionCount=1`；订阅 `/user/queue/notifications`；线程为 Rabbit listener |
| C3 BROKER_OUTBOUND | 真实 `clientOutboundChannel` 观察到同一 `notificationId=23`；目标已解析为 `/queue/notifications-user<session>`；有真实 WebSocket `sessionId` |
| C4 BROWSER_RECEIVED | Chromium raw WebSocket frame 收到同一 `notificationId=23`，时间 `2026-08-11T06:00:28.350Z` |
| C5 UI_APPLIED | 页面显示对应任务通知，时间 `2026-08-11T06:00:28.361Z` |

`stompCommand=null` 是 Spring `clientOutboundChannel` interceptor 所处的 STOMP 序列化前阶段；C3 不把日志当作网络到达证据，而是与 Chromium raw frame 的 C4 和相同 session/destination 一起构成链路证据。诊断只记录 ID、destination、session、线程和时间，不记录 JWT、Cookie、密码、Secret 或通知正文。

### 实际最小改动

1. 新增 acceptance profile 专用 `NotificationDeliveryDiagnostics`，记录 C1/C2/C3，并用 `SimpUserRegistry` 记录会话与订阅快照。
2. 在 WebSocket client outbound channel 安装 acceptance-only interceptor，验证 user destination 解析和实际 outbound payload。
3. Playwright 新增 10 次通知定向闭环，按精确 notification ID 关联 C4/C5；保留原有 9 场景和历史失败 trace。
4. 未改依赖、数据库 schema、生产 profile、通知 REST 契约或正式 push/replay 语义。

### 阶段门禁结果

- Direct targeted notification: `10/10`。
- Nginx proxy targeted notification: `10/10`。
- Direct full E2E: `2 × 9/9`。
- Nginx proxy full E2E: `2 × 9/9`。
- Stage12 reliability: 远程既有证据 `4/4`。
- OSV: 远程既有证据 `0 affected / 0 vulnerabilities`。
- npm audit: 远程既有证据 `0 vulnerabilities`。

因此 `Stage 11.5B` 从 `PARTIAL_PENDING_BROWSER_E2E` 关闭为 `COMPLETED`；总分仍保持 `85/100`，不把本地单节点 E2E 结果升级为生产 HA 或可靠消息投递结论。
