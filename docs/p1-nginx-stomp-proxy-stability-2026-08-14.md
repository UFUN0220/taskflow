# P1：Nginx WebSocket/STOMP 代理稳定性最终复验（2026-08-14）

## 结论

> 后续 Final Security Closeout 复验说明：本报告中的 20/20 与 2×19/19 是此前已完成的独立证据；本轮没有用 Log4j 修复或未验证的 C4 装饰器改写这些历史结果。新镜像初次 direct 全量为 18/19，失败场景已保存 trace/screenshot/video；撤掉未建立证据且影响连接稳定性的 C4 transport decorator，并增加通知事实持久化条件等待后，最终 direct `19/19`、proxy `19/19`。C4 仍未建立，P1 继续保持 `OPEN_WITH_DOCUMENTED_LIMIT`。

本次复验基于本地 acceptance Compose 环境、相同数据库/凭据边界和真实 Chromium。当前结果：

- direct 定向通知：20/20；
- Nginx proxy 定向通知：20/20；
- direct 完整 E2E：Run 1 19/19、Run 2 19/19；
- Nginx proxy 完整 E2E：Run 1 19/19、Run 2 19/19；
- 四轮完整 E2E 失败率：0/76；
- P1 状态：`OPEN_WITH_DOCUMENTED_LIMIT`；
- 正式评分保持：`83/100`。

历史报告中的 `9/9`、`10/10` 和 `3×9/9` 仍保留为历史证据；以上是本次独立复验结果，不将历史结果覆盖为当前结果。

## 复验边界

两条路径使用相同 acceptance profile、测试账号、MySQL/Redis/RabbitMQ/MinIO、业务 fixture、Chromium 和 Playwright 场景。主要变量是 WebSocket 地址：

- direct：浏览器将 WebSocket 指向 backend 宿主机映射端口 `18401`；
- proxy：浏览器访问 frontend Nginx `/ws/notifications`，由 Nginx 转发到 backend。

本地 Nginx 配置未修改。现有 `/ws/` location 已包含 HTTP/1.1 Upgrade、Upgrade/Connection、Host、Forwarded headers、`proxy_buffering off` 和长连接 timeout；本轮没有证据表明需要调整这些参数。

## C1-C7 证据矩阵

| 检查点 | 含义 | 当前证据 | 状态 |
|---|---|---|---|
| C1 | MySQL notification 持久化 | 定向 40 次均按 notificationId 查询到 `C1_PERSISTED` | VERIFIED |
| C2 | dispatch 请求及 SimpUserRegistry 会话/订阅快照 | 定向 40 次记录 `sessionCount=1`、`/user/queue/notifications` | VERIFIED |
| C3 | Spring clientOutboundChannel 观察 | 定向 40 次记录同一 notificationId、sessionId 和 user destination | VERIFIED |
| C4 | 实际服务器 WebSocket transport `sendMessage` 边界 | 新增 acceptance-only 观测代码，但本次运行的既有镜像未包含该类；没有可复核 C4 样本 | NOT_ESTABLISHED |
| C5 | Chromium raw WebSocket frame | direct/proxy 定向 40 次均为 true | VERIFIED |
| C6 | 浏览器 STOMP callback/解析帧 | 修复诊断字段后 direct/proxy 定向 40 次均为 true | VERIFIED |
| C7 | UI 应用通知 | direct/proxy 定向 40 次均为 true | VERIFIED |

`C6=false` 的早期输出是测试观测 bug：检查代码使用了字面量 `MESSAGE\\n`，而实际测试断言使用了正确换行。该问题已修正，未改变真实 MESSAGE 断言。

## Principal、READY 和业务路径

本次 C2 快照显示真实 session 与订阅已存在，订阅为 `/user/queue/notifications`；direct/proxy 均完成 CONNECT、CONNECTED、SUBSCRIBE、READY 和业务 MESSAGE。READY 与业务通知使用相同的 user-destination 配置和已认证 Principal 链路。

本次日志未记录密码、完整 JWT、Cookie、Secret 或通知正文。由于 C4 未建立，不能声称已经在服务器物理 WebSocket `sendMessage` 边界完成 C3→C4 的独立隔离证明。

## Root cause 分类

本次复验不能复现历史的 proxy-only MESSAGE 缺失，因此当前不能把问题归因于 Nginx URI rewrite、Upgrade、buffering、Origin、Cookie、Principal 或 timeout。历史失败仍应保留为 `NOT_REPRODUCED / OBSERVATION-RACE-CANDIDATE`，而不是改写成已证明的 Nginx 根因。

更准确的当前结论是：本地单 backend、Spring simple broker、同源 Cookie 和 Nginx `/ws/` proxy 的业务链路在本轮 76 个浏览器场景中全部成功；WebSocket 仍是 best-effort push，通知事实和断线恢复依赖 MySQL/REST 补拉，不是可靠消息投递。

## 运行命令与结果

- `npm run e2e -- --grep 通知定向闭环`，direct：20/20；
- `npm run e2e -- --grep 通知定向闭环`，proxy：20/20；
- `npm run e2e`，direct Run 1：19/19；
- `npm run e2e`，direct Run 2：19/19；
- `npm run e2e`，proxy Run 1：19/19；
- `npm run e2e`，proxy Run 2：19/19；
- 运行时 acceptance Compose 六个服务均为 healthy；
- direct 初次超时原因：测试指定默认 `28080`，但当前 Compose 只映射 backend `18401`；改用已存在的 `18401` 后通过；
- acceptance backend 重新打包/镜像构建成功，但后续无损重建操作被执行审批通道拒绝，因此 C4 未计入通过。

## 评分与后续边界

本轮不调整 83/100。浏览器业务证据增强了“运行集成与恢复就绪度”的可解释性，但 C4 未建立、Nginx 仍是本地单节点链路、WebSocket 仍非持久可靠投递，不能机械加分或关闭 P1。

若未来仅在明确授权且不删除卷的条件下，用包含本轮诊断类的新 acceptance 镜像复验出 C4，并保持同等 direct/proxy 结果，才可将 C4 从 `NOT_ESTABLISHED` 改为 `VERIFIED`；仍不能升级为生产 HA 或可靠消息投递。

## 证据文件

Playwright HTML report、trace、截图和视频继续保留在 `frontend/playwright-report/`、`frontend/test-results/`。本轮成功运行没有依赖 retry 隐藏失败；失败证据目录保留历史失败材料。
