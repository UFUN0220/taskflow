# TaskFlow Platform 阶段 11.6 浏览器 E2E 报告

日期：2026-08-10

## 结论

阶段 11.6 已关闭本地 Compose Nginx WebSocket 代理路径的 P1：

- Backend direct：9/9；
- Nginx proxy：连续 3 轮均为 9/9；
- Nginx proxy 修复后未再出现 Playwright worker 复用测试用户导致的 409；
- 浏览器真实收到 STOMP `MESSAGE`，并由页面通知中心显示业务通知；
- 不代表云生产 Ingress、跨节点 HA、负载均衡或真实证书轮换已经验证。

## A/B 冻结条件

A/B 使用相同 acceptance profile、数据库与持久卷、测试管理员、Chromium、业务 fixture 和 9 个 Playwright 场景。主要变量仅为 WebSocket 地址：

- A：浏览器页面通过 backend direct WebSocket 端口访问 `/ws/notifications`；
- B：浏览器页面通过 frontend Nginx `/ws/notifications` 代理访问 backend。

浏览器测试使用 HttpOnly Cookie 认证；STOMP 浏览器连接未把 JWT 放入 URL，也未伪造用户 ID。敏感 Cookie、密码、Token 和消息正文没有写入报告或日志。

## 根因

后端 endpoint、URI、Cookie 握手认证和 Principal 链路并不是 A/B 差异点。两条路径都能够完成 Upgrade、STOMP CONNECT/CONNECTED 和 SUBSCRIBE，且业务通知已经持久化到数据库。

真实问题是前端在收到 `CONNECTED` 后立即把连接状态标记为“实时连接”，但此时 Spring Simple Broker 还可能没有完成 inbound `SUBSCRIBE` 的处理。direct 路径延迟较低，通常掩盖了这个竞态；Nginx 路径的额外转发时序使竞态稳定暴露，因此出现 CONNECTED、SUBSCRIBE 都可见但业务 MESSAGE 不稳定的现象。

最初尝试使用 STOMP `receipt` 等待 broker 回执，但当前 Spring Simple Broker 配置不会为该 SUBSCRIBE 稳定产生 RECEIPT，已撤销该方案。最终方案是在 SUBSCRIBE 帧发送后，再发送已认证的 `/app/notifications/ready` 应用消息；后端只有在处理该消息时才向同一 `/user/queue/notifications` 发送 `SUBSCRIPTION_READY` 标记。浏览器仅在收到该真实 MESSAGE 后标记订阅就绪，随后才允许业务事件测试继续。

## Nginx 修改前后

原 `/ws/` location 已经使用 HTTP/1.1、Upgrade、Connection、Host、X-Forwarded-For 和 X-Forwarded-Proto，且 `proxy_pass http://backend:8080` 不携带 URI，因此没有证据表明发生了 `/ws` URI 重写错误。

本阶段保留原有路径语义，只补充长连接边界：

```nginx
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-Host $host;
proxy_set_header X-Forwarded-Port $server_port;
proxy_buffering off;
proxy_cache_bypass $http_upgrade;
proxy_read_timeout 1h;
proxy_send_timeout 1h;
```

容器内 `nginx -T` 已确认这些配置实际加载。它们改善了 WebSocket tunnel 的可观测边界和长连接行为，但本次 MESSAGE 闭环的主要修复是前端订阅就绪握手与后端 `/app/notifications/ready`，不是关闭 WebSocket 鉴权或改变 `/user` 路由。

## A/B 生命周期对照

| Layer | Backend direct | Nginx proxy |
|---|---|---|
| WebSocket Upgrade | 101 | 101 |
| WebSocket URL | `/ws/notifications`，backend direct 端口 | `/ws/notifications`，frontend `/ws/` location |
| STOMP CONNECT | 发送并成功 | 发送并成功 |
| STOMP CONNECTED | 收到 | 收到 |
| SUBSCRIBE | `/user/queue/notifications` | `/user/queue/notifications` |
| Subscription ready | `/app/notifications/ready` 后收到真实 `SUBSCRIPTION_READY` MESSAGE | 同样收到真实 `SUBSCRIPTION_READY` MESSAGE |
| 业务事件 | 真实任务业务写入 | 真实任务业务写入 |
| 业务 MESSAGE | 浏览器收到，包含当前任务标识 | 浏览器收到，包含当前任务标识 |
| UI 通知 | 显示通知 | 显示通知 |
| 完整场景 | 9/9 | 9/9 |

请求层面，代理路径实际使用 `/ws/notifications`，返回 101；Nginx location 转发到同名 backend endpoint。Cookie 认证沿用现有同源/acceptance 配置；Authorization、密码和完整 Token 不记录。代理路径额外传递 X-Real-IP、X-Forwarded-Host、X-Forwarded-Port，并关闭 buffering、延长 tunnel 超时。

## Principal 与 user destination

两条路径都由服务端从已认证 Cookie 恢复用户身份，握手与 STOMP channel interceptor 使用同一个认证用户；前端没有传入 userId 作为接收依据。后端推送用户名使用用户 ID，WebSocket Principal 也由 `NotificationWebSocketHandshakeHandler` 对齐为同一用户 ID，因此 `convertAndSendToUser()` 与 `/user/queue/notifications` 的映射一致。

验证依据是：两条路径均能在同一用户会话下收到订阅就绪 MESSAGE 和随后由真实任务事件产生的业务 MESSAGE。当前报告不把未持久化的临时调试日志当作生产观测证据。

## STOMP 帧顺序

修复后的浏览器序列为：

```text
CONNECT
CONNECTED
SUBSCRIBE destination:/user/queue/notifications
SEND destination:/app/notifications/ready
MESSAGE notificationType:SUBSCRIPTION_READY
业务写操作
MESSAGE 真实任务通知
页面通知中心更新
DISCONNECT
```

Playwright WebSocket tracker 保留发送/接收帧并断言真实 `MESSAGE` 帧中包含业务任务标识；测试没有 mock WebSocket，也没有只以 HTTP 101 作为通过条件。

## 稳定性与 409

Nginx proxy 修复后的完整 E2E：

| Run | Result |
|---|---|
| 1 | 9 passed |
| 2 | 9 passed |
| 3 | 9 passed |

backend direct 修复后回归：9 passed。

本阶段早期 direct 回归曾发现附件测试生成的 `taskNo` 超过后端 63 字符约束；这不是通过忽略 409 处理，而是修正 fixture：run 标识加入 worker 进程隔离，并限制 task 前缀长度，使 worker 重启后仍能使用唯一且合法的测试数据。修复后的三轮 proxy 与 direct 回归均未出现 409。

## 回归证据

- `npm run typecheck`：通过；
- `npm run build`：通过；仍有 Vite 主 chunk 超过 500 KB 的既有提示；
- `docker compose ... build backend`：通过；
- `docker compose ... build frontend`：通过；
- `mvnw.cmd test`：79 tests，0 failures，1 skipped；
- `mvnw.cmd "-Dtaskflow.integration=true" verify`：BUILD SUCCESS，Testcontainers 环境与 Flyway V1-V8 迁移通过，覆盖率检查通过；
- `docker exec ... nginx -T`：确认 `/ws/` location 的代理配置已加载；
- Playwright：proxy 3×9/9，direct 1×9/9。

## 剩余边界

本报告只证明 Windows 本地 Docker Compose、单 backend、单 frontend Nginx 和当前 acceptance 数据环境。仍未证明多副本 broker 路由、云 Ingress、跨节点 HA、LB idle timeout 策略、生产证书轮换、跨域 Cookie 部署和真实多实例 WebSocket session registry。
