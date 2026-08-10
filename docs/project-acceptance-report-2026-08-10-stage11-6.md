# TaskFlow Platform 验收报告增补：阶段 11.6

日期：2026-08-10

## 评分处理

本次没有为了修复 Nginx 代理路径而机械调整总分。阶段 7 的 85/100 保持不变：本阶段把“本地 Nginx WebSocket P1”从未关闭更新为已关闭，但新增的证据仍然属于 Docker Compose 单节点本地验证，不能升级为生产可用性、HA 或云 Ingress 得分。

## 新增可复核证据

- Nginx proxy：Playwright Chromium 连续 3 轮，每轮 9/9；
- backend direct：修复后 9/9；
- 真实 STOMP MESSAGE：浏览器 tracker 观察到 `/user/queue/notifications` MESSAGE，且业务断言包含真实任务标识并驱动页面通知；
- 订阅就绪：`SUBSCRIBE` 后通过已认证 `/app/notifications/ready` 获取 `SUBSCRIPTION_READY` MESSAGE；
- 后端回归：`mvnw.cmd test` 79 tests，0 failures，1 skipped；
- 集成回归：`mvnw.cmd "-Dtaskflow.integration=true" verify` BUILD SUCCESS，Testcontainers 与 Flyway V1-V8 验证通过；
- Nginx 配置：容器内 `nginx -T` 已确认 WebSocket location 实际加载 `proxy_buffering off`、长连接 timeout 和 forwarded header 配置；
- worker 409：fixture 使用 worker 进程隔离并限制任务编号长度，修复后四次完整运行没有 409。

## 根因与边界

根因是前端在 CONNECTED 后过早将连接视为可用，Nginx 转发时序放大了 SUBSCRIBE 尚未真正处理完成的竞态；不是通过 anonymous SUBSCRIBE、放宽权限、伪造 userId 或移除 Cookie 认证解决。backend direct 低延迟时掩盖了该问题。

两条路径都保留服务端 Principal、`/user` destination 和 Redis/数据库认证边界。新增加的 `/app/notifications/ready` 是认证后的应用级订阅确认，不承载敏感数据。Nginx 仍使用 `/ws/notifications` 原路径转发，未引入 SockJS 或重写 WebSocket endpoint。

本地证据不能证明：生产 TLS/Ingress、云 LB idle timeout、跨节点 WebSocket session 共享、broker 高可用、跨 AZ 恢复或真实证书轮换。生产发布结论仍不通过。

## 文档索引

完整 A/B 帧序列、Nginx 修改前后、Principal 对照与三轮稳定性记录见：

- [阶段 11.6 浏览器 E2E 报告](e2e-browser-report-2026-08-10-stage11-6.md)
