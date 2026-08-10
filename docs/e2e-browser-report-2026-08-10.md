# 阶段 10：浏览器认证通知闭环复验（2026-08-10）

## 结论

在隔离的 acceptance Compose 环境中，使用真实 Chromium、Cookie 认证和真实任务状态事件完成了 **3 次连续完整运行，每次 9/9，通过总计 27/27，0 失败**。WebSocket 场景不是 Mock：浏览器完成 `CONNECT → CONNECTED → SUBSCRIBE`，服务端产生任务状态通知，浏览器收到包含本次任务编号的 STOMP `MESSAGE`，通知中心显示对应内容。

本结果代表 Windows 本机上的单后端、Spring simple broker 和 acceptance Compose 实测，不代表多实例 WebSocket 广播、生产 HA 或云端容量。

## 复现环境

| 项目 | 实际值 |
| --- | --- |
| 浏览器 | Playwright Chromium（Playwright 1.62.1） |
| 前端入口 | `http://127.0.0.1:25173` |
| 后端入口 | acceptance 容器映射端口 `28080`，浏览器通过前端代理访问 |
| 认证 | HttpOnly `TASKFLOW_ACCESS` Cookie；STOMP CONNECT 不携带 JWT |
| 数据 | 隔离 acceptance 数据库和六服务 Compose；未删除既有卷 |
| Playwright 配置 | 1 worker，CI retry 配置未用于本地绿灯 |

## 场景矩阵

| 场景 | 第 1 次 | 第 2 次 | 第 3 次 |
| --- | --- | --- | --- |
| 登录、`/api/auth/me`、无 localStorage JWT | 通过 | 通过 | 通过 |
| 未登录 401 和受保护页面回登录页 | 通过 | 通过 | 通过 |
| 普通用户 UI/后端 403 | 通过 | 通过 | 通过 |
| 任务列表、详情、创建、更新 | 通过 | 通过 | 通过 |
| 重复提交保护 | 通过 | 通过 | 通过 |
| 登出后旧会话失效 | 通过 | 通过 | 通过 |
| 浏览器真实 STOMP 通知 | 通过 | 通过 | 通过 |
| 断线重连与 HTTP 未读补拉 | 通过 | 通过 | 通过 |
| 附件上传正向链路 | 通过 | 通过 | 通过 |

完整运行命令：

```powershell
$env:TASKFLOW_ACCEPTANCE_BASE_URL = "http://127.0.0.1:25173"
$env:TASKFLOW_ACCEPTANCE_RUN_ID = "<本次运行编号>"
npm run e2e -- --workers=1
```

管理员用户名、管理员密码和测试用户密码只通过当前终端或 CI Secret 注入；报告不保存实际值。

## Root cause 与修复

原始失败有两个相互关联的原因：

1. WebSocket 握手/连接 Principal 可能使用登录名，而通知发送使用稳定的数据库用户 ID，导致用户目的地无法匹配；另外 CONNECT 设置的 Principal 没有可靠传播到后续 SUBSCRIBE 帧，表现为订阅后 `ERROR/1002`。
2. E2E 夹具将小写运行编号拼入任务编号，违反后端的大写编号约束；通知展示断言错误地搜索任务标题，而通知内容按设计展示任务编号。

本阶段修复为：

- 握手 Handler 仅从已验证的 HTTP Authentication 推导 `NotificationWebSocketPrincipal(userId, username)`；浏览器不能提供或覆盖 userId。
- CONNECT 认证后将 Principal 放入 STOMP 会话属性，后续 SUBSCRIBE/消息帧恢复同一身份。
- 恢复 Spring 标准 `convertAndSendToUser(String.valueOf(userId), "/queue/notifications", payload)` 路径，不使用前端 userId 或非标准目标地址绕过。
- E2E 运行编号规范化为大写安全任务编号；通知断言要求真实 `MESSAGE` 包含本次任务编号，并要求通知抽屉显示该编号。
- 断线场景先关闭浏览器 WebSocket，再触发真实任务状态事件，验证 REST 未读补拉和有限退避重连。

## 证据

- 最终 HTML 报告：`frontend/playwright-report/index.html`
- 失败时的截图、视频、trace 仍由 Playwright 保存在 `frontend/test-results/`；最终 3 次运行未产生失败项。
- WebSocket 断言同时观察页面原生 WebSocket 帧和应用解析后的 STOMP 帧，不把后端单测或服务器内部日志当作浏览器 MESSAGE 证据。

## 边界与剩余风险

- 当前验证使用 Spring 内置 simple broker 和单后端容器；多副本跨实例消息广播仍需外部 broker 或共享广播设计，不能由本报告推出生产 HA。
- WSS、真实 Ingress Controller、证书轮换、跨节点连接迁移和云 LB 未在本报告中验证。
- 调试期间曾使用临时本地诊断日志定位帧路由；诊断代码已从源码移除，后续代码路径不记录 Token/Cookie。若将该 acceptance 环境用于共享机器，应按环境策略轮换测试 Secret 并清理 Docker daemon 历史日志。

