# Stage 11.5B-E2E：Spring Boot 3.5.16 升级后浏览器回归

## 结论

本次回归使用提交 `1bc86f349966fe7f9a5ccfc36d295927a4127514` 的当前源码、重新打包的 Spring Boot 3.5.16 后端和真实 Chromium。acceptance Compose 健康与 Cookie/CSRF smoke 通过，但浏览器通知场景出现可重复的偶发性：首轮未收到业务 STOMP `MESSAGE`，随后同环境第二轮通过。

因此本批次保持：

- `Stage 11.5B = PARTIAL_PENDING_BROWSER_E2E`
- Candidate = `PARTIAL_PENDING`
- 评分保持 `85/100`
- Stage 13 不允许开始

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

通过 `scripts/acceptance-up.ps1` 和 `scripts/acceptance-check.ps1` 验证：

- MySQL、Redis、RabbitMQ、MinIO healthy；
- 后端 healthy，Flyway 新库迁移到 V8；
- 管理员登录成功；
- HttpOnly/SameSite Cookie 设置成功；
- `/api/auth/me`、任务列表成功；
- CSRF bootstrap 和 logout 成功；
- logout 后旧会话返回 401。

为兼容当前 Windows PowerShell，`acceptance-check.ps1` 改为通过异常响应解析 4xx/5xx，不再使用当前 shell 不支持的 `-SkipHttpErrorCheck`。

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

1. direct/proxy 均未取得连续两轮 9/9，故不具备 11.5B 最终关闭证据。
2. 需要继续定位并补充业务通知的可靠 push/replay 或可观测确认语义；不能用重复执行或无限 retry 把偶发失败包装成稳定通过。
3. Stage 13 保持未开始。
