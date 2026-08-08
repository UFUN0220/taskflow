# 阶段 11：WebSocket 通知中心

## 功能边界

阶段 10 负责将提醒和任务状态事件可靠地消费并写入 `notification`。阶段 11 在此基础上增加 STOMP over WebSocket 实时推送。数据库通知仍是最终事实，WebSocket 只负责在线用户的实时体验；断线、浏览器休眠或推送失败后，客户端通过 HTTP 未读接口补拉。

## 连接与认证

WebSocket 端点为 `/ws/notifications`。HTTP Upgrade 请求允许进入端点，但真正的认证发生在 STOMP `CONNECT` 帧：

```text
CONNECT
accept-version:1.2
Authorization:Bearer <JWT>

\0
```

服务端使用与 HTTP API 相同的 `JwtTokenService` 重新校验签名、过期时间、用户 ID 和当前用户状态，然后把用户 ID 设置为 WebSocket Principal 名称。客户端不能在订阅路径中指定其他用户 ID。

客户端订阅固定目的地：

```text
/user/queue/notifications
```

服务端使用 `convertAndSendToUser(userId, "/queue/notifications", payload)` 推送。用户目的地由服务端认证 Principal 解析，不能由前端传入的 `userId` 决定。

## 多设备和断开清理

`NotificationWebSocketSessionRegistry` 以 `userId -> sessionId 集合` 维护在线连接：

- 同一用户的多个浏览器、标签页和设备分别登记，不会互相覆盖；
- `SessionDisconnectEvent` 删除对应 session，集合为空时删除用户条目；
- 连接登记只用于在线状态和诊断，不作为通知持久化依据；
- 服务重启后在线状态丢失不会影响数据库通知，客户端重新建立连接后通过 HTTP 补拉。

## 推送时机和失败隔离

通知插入数据库成功后发布 `NotificationCreatedEvent`，由事务提交后的监听器执行 WebSocket 推送。重复消息不会生成新的通知，也不会重复发布新的推送事件。推送异常只记录 `userId`、通知 ID 和业务聚合 ID，不回滚已经提交的通知或任务状态事务。

## 前端行为

`frontend/src/notifications/NotificationCenter.tsx` 提供最小可用通知中心：

1. 使用当前访问 Token 调用未读通知和未读数量接口；
2. 建立 STOMP WebSocket 连接并订阅当前用户目的地；
3. 收到实时通知后更新列表和未读数量；
4. 连接断开时有限次数退避重连；
5. 支持单条已读、全部已读和手动 HTTP 刷新；
6. 没有 Token 时不建立连接，只显示登录提示。

开发环境 Vite 将 `/ws` 代理到后端 `8080` 端口。允许来源默认限制为 `localhost:5173` 和 `127.0.0.1:5173`，可通过 `WEBSOCKET_ALLOWED_ORIGINS` 调整。

## 验收场景

- 有效 Token 能连接并收到自己的通知；
- 缺少 Token、过期 Token、禁用用户不能完成 STOMP CONNECT；
- 伪造 `/user/{otherUserId}/...` 不会改变服务端用户目的地；
- 同一用户两个连接都能收到同一条已落库通知；
- 断开后 session 登记被清理；
- WebSocket 推送失败不影响通知落库和任务状态变更；
- 断线重连后通过 HTTP 未读接口可以补回离线期间通知；
- `WEBSOCKET_ENABLED=false` 时不注册 WebSocket 推送组件，HTTP 通知接口仍可独立使用。

## 当前限制

- 当前项目使用 Spring 内置 simple broker，未引入外部 STOMP broker；单实例和本地多连接场景满足学习目标。多实例部署时需要外部 broker 或跨实例消息广播方案。
- 前端登录页面属于后续阶段；阶段 11 读取 `taskflow.accessToken` 或 `accessToken`，登录流程接入时应统一写入其中一个键。
- 浏览器原生 WebSocket 不允许自定义 HTTP `Authorization` 请求头，因此 Token 放在 STOMP CONNECT 帧而不是 URL 查询参数中，避免把 Token 写入访问日志。
