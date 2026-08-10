# 阶段 3：浏览器 E2E 与 WebSocket 验收记录（2026-08-09）

## 结论

本阶段新增 Playwright 浏览器测试套件，共 9 个场景；测试夹具会创建带运行编号的普通用户和任务，结束时标记测试用户禁用并清理仍为草稿的任务，不删除数据库卷。Playwright CLI 未安装在预置工具路径中，因此使用项目内 `@playwright/test` 完成浏览器验证。

截至本报告生成时，最后一次使用现有 Compose 后端、源码前端和真实浏览器的可复核结果为 **9 个场景：4 通过、5 失败**。失败均保留了截图、视频或 trace；后续已修正登录完成等待、Modal 提交定位、重复标题隔离和 WebSocket 会话 Principal 传播，但由于当前临时后端实例无法使用现有数据库管理员密码，修复后的完整真实链路尚未重新跑完，不把它计为全绿。

## 场景矩阵

| 场景 | 结果 | 证据/说明 |
| --- | --- | --- |
| 登录成功、进入系统、`/api/auth/me` | 通过 | 最后一次完整运行通过 |
| 未登录访问受保护 API 返回 401、受保护页面回到登录页 | 通过 | 最后一次完整运行通过 |
| 普通用户 UI 隐藏审批、直接调用审批接口返回 403 | 通过 | 最后一次完整运行通过 |
| 任务列表、详情、创建、更新 | 未完成 | 首轮等待/Modal 定位问题已修正，修复后尚未完整复跑 |
| 创建请求重复提交保护 | 未完成 | 同上；测试通过路由延迟观察请求计数和按钮禁用 |
| 登出后旧 Token 失效 | 通过 | 最后一次完整运行通过，旧会话访问 `/api/auth/me` 返回 401 |
| 浏览器真实收到 STOMP 通知 | 未通过 | 浏览器真实 WebSocket 收到服务端 `CONNECTED`，但现有 Compose 后端在 SUBSCRIBE 后返回 STOMP `ERROR` 并关闭 1002；不是 Mock 成功 |
| WebSocket 断线重连后 HTTP 补拉 | 未完成 | 真实订阅前置条件失败；前端已增加 CONNECTED 后未读通知补拉 |
| 附件上传正向链路 | 未完成 | 夹具和文件选择链路已实现，完整运行受前置环境/定位问题影响 |

## WebSocket 证据边界

本次浏览器调试证据显示：

- 浏览器确实创建了 `/ws/notifications` 原生 WebSocket；
- 服务端返回了 STOMP `CONNECTED`；
- 随后的订阅帧收到 STOMP `ERROR`，连接以 1002 关闭；
- 因此“浏览器客户端真实收到消息”目前不能宣称通过；后端已有的单元测试也不能替代这条证据。

为修复 CONNECT 后续帧的身份传播，`NotificationWebSocketChannelInterceptor` 现在会将已通过 JWT/Redis 会话校验的 Principal 绑定到 STOMP 会话属性，并在后续 SUBSCRIBE 等帧恢复；新增回归测试覆盖该行为。该修复已通过后端单元测试，但尚未获得修复后真实 Compose WebSocket 的全链路绿灯。

## 失败证据目录

Playwright 默认保留以下类型的证据（路径位于 `frontend/test-results/`，已加入 `.gitignore`，本地仍可复查）：

- `taskflow-浏览器真实收到-STOMP-通知消息/test-failed-1.png`
- `taskflow-浏览器真实收到-STOMP-通知消息/trace.zip`
- `taskflow-任务列表、详情、创建和更新真实写链路/test-failed-1.png`
- `taskflow-普通用户无审批权限时-UI-隐藏操作且后端返回-403/trace.zip`

报告和 trace 不应提交到 Git，也不得包含管理员密码、JWT、Cookie 或完整 Token。

## 运行方式

先启动 Compose 六服务，并准备只存在于当前终端的管理员密码和测试用户密码：

```powershell
Set-Location frontend
$env:PLAYWRIGHT_BROWSERS_PATH = "F:\newinstall\playwright-browsers"
npx playwright install chromium
npm run dev -- --host 127.0.0.1 --port 5173
```

另开终端执行：

```powershell
Set-Location frontend
$env:E2E_BASE_URL = "http://127.0.0.1:5173"
$env:E2E_ADMIN_USERNAME = "admin"
$env:E2E_ADMIN_PASSWORD = "<通过安全方式注入的本地管理员密码>"
$env:E2E_TEST_USER_PASSWORD = "<本次运行专用测试密码>"
$env:PLAYWRIGHT_BROWSERS_PATH = "F:\newinstall\playwright-browsers"
npm run e2e
```

`E2E_ADMIN_PASSWORD`、`E2E_TEST_USER_PASSWORD` 不进入 `.env.example`、日志、报告或 Git。当前测试默认使用源码前端的 5173 端口，因为 Compose 后端默认只允许 localhost/127.0.0.1:5173 的 WebSocket Origin；若使用其他端口，必须通过安全的运行时环境变量同步调整 `WEBSOCKET_ALLOWED_ORIGINS`。

## 当前评分影响

阶段 3 已将浏览器测试从“代码存在”推进到“真实浏览器部分执行并保存失败证据”，并修正了一处后续 STOMP 帧身份传播缺口。但由于 9 场景尚未全通过，阶段 11、13、14 及测试可信度评分暂不机械加分；待修复后在同一 Compose 数据边界下完成 9/9，且至少 WebSocket 场景收到真实 `MESSAGE` 帧，再重新评估。

## 后续阶段 10 复验

本文件保留 2026-08-09 的历史失败基线。2026-08-10 已在隔离 acceptance Compose 环境使用真实 Chromium 完成 3 次连续完整运行，每次 9/9；真实 STOMP `MESSAGE` 到通知中心、断线重连和 HTTP 补拉均通过。当前结论和场景矩阵以 [`docs/e2e-browser-report-2026-08-10.md`](e2e-browser-report-2026-08-10.md) 为准。
