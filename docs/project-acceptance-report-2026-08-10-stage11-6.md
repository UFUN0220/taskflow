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

## 阶段 12 正式验收增补

PR #1 的远程 integration-security job `93444588147` 在包含提交 `4681da9e94352e09fb3337a484ec556e5533d3fd` 的 merge ref 上执行 `bash ./mvnw -Dtaskflow.integration=true verify`，结果为 84 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS。Stage12 四个 testcase 的真实能力映射与故障注入边界见 [阶段 12 故障注入验收](fault-injection-acceptance-2026-08-10.md)。

该 job 后续 OWASP 步骤因未提供 NVD API key，下载 NVD 数据至 48% 时收到 runner shutdown，退出码 143，job 为 `cancelled`；这是扫描基础设施/运行时中断，不是确认的 CVE gate 结果。npm audit 远程报告为 0 vulnerabilities，OWASP 不计为通过，`integration-security` 整体仍不是 PASS。

本次只增加 Stage12 的远程可靠性证据，不机械上调评分；总分继续保持 85/100，生产发布结论继续不通过。

## 阶段 12.4：替代依赖安全门禁

阶段 12.4 停止依赖或申请 `NVD_API_KEY`。OWASP Maven `security-scan` profile 保留为 `SUPPLEMENTAL_NVD_REMOTE_BLOCKED`，本地历史报告仍记录真实高危/严重候选，不能写成 OWASP PASS。

Google 官方 OSV-Scanner `v2.5.0` 已作为同一 workflow 的主依赖漏洞门禁真实执行：解析 `pom.xml` 27 packages、`frontend/package-lock.json` 220 packages；发现 21 个 Maven package、70 个漏洞（7 Critical、27 High、27 Medium、9 Low），SARIF artifact `9070667075` 已上传并进入 Code Scanning。OSV reporter 因漏洞发现失败，分类为 `VULNERABILITY_GATE_FAILURE`，不是扫描基础设施失败。

本次 integration-security 的 Maven/Testcontainers、覆盖率、npm audit、npm gate 和 OWASP supplemental 均通过；OSV 主门禁按设计阻断 workflow。该结果证明主门禁可执行，但不代表依赖治理完成；评分继续保持 85/100，OSV findings 进入依赖归因与后续小批治理，未在本阶段升级依赖，也未开始阶段 13。

## 根因与边界

根因是前端在 CONNECTED 后过早将连接视为可用，Nginx 转发时序放大了 SUBSCRIBE 尚未真正处理完成的竞态；不是通过 anonymous SUBSCRIBE、放宽权限、伪造 userId 或移除 Cookie 认证解决。backend direct 低延迟时掩盖了该问题。

两条路径都保留服务端 Principal、`/user` destination 和 Redis/数据库认证边界。新增加的 `/app/notifications/ready` 是认证后的应用级订阅确认，不承载敏感数据。Nginx 仍使用 `/ws/notifications` 原路径转发，未引入 SockJS 或重写 WebSocket endpoint。

本地证据不能证明：生产 TLS/Ingress、云 LB idle timeout、跨节点 WebSocket session 共享、broker 高可用、跨 AZ 恢复或真实证书轮换。生产发布结论仍不通过。

## 文档索引

完整 A/B 帧序列、Nginx 修改前后、Principal 对照与三轮稳定性记录见：

- [阶段 11.6 浏览器 E2E 报告](e2e-browser-report-2026-08-10-stage11-6.md)
