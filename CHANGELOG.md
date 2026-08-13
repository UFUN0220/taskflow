# Changelog

- Stage 13 Final Freeze（2026-08-14）：完整 npm audit 的唯一 high 已定位为 `vite@6.4.3 → postcss@8.5.26 → nanoid@3.3.17` 间接开发依赖（advisory 1139427），通过精确 `overrides` 更新为 `nanoid@3.3.18`。`npm ci` 后完整 audit 与生产 audit 均为 0 vulnerabilities；typecheck/build、Chromium direct/proxy 9/9、后端默认回归和显式 Testcontainers verify 均完成。入口 JS 保持 583,825 B，Vite 大 chunk warning 与性能因果边界保留，评分维持 85/100。

- Stage 13 容器复验补充（2026-08-14）：Docker Desktop daemon 恢复后，在 `desktop-linux` context 下真实执行 `-Dtaskflow.integration=true verify`，85 tests、0 failures、0 errors、0 skipped；Stage12 4/4、Stage14 1/1，Flyway V1-V8 与 JaCoCo 门禁通过。此前本机 daemon 未运行造成的跳过不再作为最终状态。

- Stage 13（2026-08-11）：将 NotificationCenter 从 App 首屏静态依赖改为懒加载；入口 JS 从 767,334 B 降至 583,825 B，Vite 大 chunk warning 保留。按 10 部门/100 用户/1000 任务、20 并发、10 秒预热、60 秒采样完成 Before/After 各 3 轮，六场景错误率均为 0；因单机方差和前端/后端因果边界，性能结论标记 `PARTIAL_CAUSALITY`，评分保持 85/100。真实 Chromium 回归修正入口配置后 direct 9/9、Nginx proxy 9/9。完整 npm audit 暴露 Vite/PostCSS 间接开发依赖 nanoid 3.3.17 的 1 个 high；生产依赖 audit 为 0，本阶段未扩大依赖升级。2026-08-14 本机复验默认 Maven 85/0/0/5，显式 verify BUILD SUCCESS 但 Docker daemon 未运行，Testcontainers 4+1 项跳过，未计为本机容器通过。

- Stage 11.5B-R：Spring Boot 3.5.16 候选通过远程 Maven/Testcontainers、JaCoCo、npm audit 和 OSV 门禁；84 tests、Stage12 4/4，OSV 从 21/70（7 critical、27 high）降为 0/0。补充 Jackson 2.21.5、Netty 4.1.136.Final、Commons Lang3 3.18.0、BouncyCastle 1.84 的精确固定。升级后浏览器 E2E 因缺少 acceptance 凭据暂未复验，评分保持 85/100，Stage 13 未开始。

- Stage 11.5B（2026-08-11）：冻结 OSV 70 条漏洞基线并完成 Maven 运行时/测试作用域归因；确认 Spring Boot 3.4.8 没有可复核的更高 3.4.x patch 后，仅提交 3.4.8→3.5.16 parent/BOM 小批候选。未手工覆盖 Spring Framework、Security、Tomcat、Jackson 或 Netty；本批次等待本地与远程完整回归，评分保持 85/100，未开始 Stage 13。

- 阶段 12.4：停止依赖不可满足的 `NVD_API_KEY` CI 前置；保留 OWASP `security-scan` 作为 `SUPPLEMENTAL_NVD_REMOTE_BLOCKED`，新增 Google 官方 OSV-Scanner v2.5.0 reusable workflow 作为 Maven/npm 主依赖漏洞门禁。首次远程扫描发现 21 个 Maven package、70 个漏洞并正确阻断，SARIF 已上传；建立 OWASP/OSV artifact 归因文档。当前评分保持 85/100，未开始阶段 13。

- 阶段 12.2：远程 GitHub Actions 已确认 Stage12 Testcontainers 4/4（整体 Maven 84/0/0）通过；fast-check 通过，npm audit 为 0 vulnerabilities。OWASP 因无 NVD API key 长时间下载后 runner shutdown 被取消，依赖安全门禁仍未闭环，评分保持 85/100。

## GitHub Actions CI 修复与阶段 12 本地运行验证 - 2026-08-10

- 删除 workflow job 级别无效的 `runner.temp` Maven 用户目录引用，解决 workflow 解析阶段 0 秒失败；fast-check 已真实通过。
- 修复附件文件名在 Linux runner 上未规范化 Windows 反斜杠的问题，完整本地 Maven 回归 84 tests、0 failures、5 skipped。
- Stage12 Testcontainers 本地真实通过 4 tests、0 failures、0 skipped：Rabbit retry/DLQ/replay/幂等、Redis 派生状态重建、MinIO 成功/失败/孤儿补偿、MySQL 同容器 restart 后 Flyway V8 与事实快照保持。
- integration-security 的远程复验已完成 Maven/Testcontainers/JaCoCo 与 npm audit；OWASP 因 NVD 无 API key 长时间下载后 runner shutdown 被取消。此前暴露的 Maven 失败时依赖扫描输出文件不存在问题，本轮已增加 always 条件和缺失输出 fail-closed 检查。

## 2026-08-10 — 调优阶段 11.6

- 修复 Nginx WebSocket/STOMP 代理路径下因订阅确认竞态导致的真实通知不稳定问题。
- 增加订阅就绪应用消息闭环，浏览器仅在收到真实 `MESSAGE` 后进入实时连接状态。
- 补充 Nginx WebSocket 长连接代理配置，并修正 Playwright worker 重启时的唯一测试数据与 `taskNo` 长度约束。
- 本地 Compose 证据：Nginx proxy 连续 3×9/9，backend direct 9/9；不外推为生产 HA 结论。

## 调优阶段 11：依赖漏洞治理与 CI 质量门禁 - 2026-08-10

- 将 `react-router-dom`/`react-router` 从 6.30.4 升级到 7.18.2；官方 npm audit 复验为 moderate/high/critical 全 0，完成 npm ci、typecheck、build 回归。
- 将直接 OkHttp 依赖升级到 5.4.0；Maven/OWASP 最终报告仍含 19 个依赖条目、79 条 high 和 25 条 critical，未添加未经证明的 suppression，也未宣称依赖零漏洞。
- 为 `security-scan` profile 增加可注入 Dependency-Check 数据目录、`NVD_API_KEY` 和 fail-closed 配置；integration-security 最终 gate 对无效报告、扫描非零和 high/critical 阻断。
- 复验发现后端直连 Chromium STOMP 9/9，但当前前端 Nginx `/ws` 代理路径未收到 MESSAGE；README、依赖报告和总验收报告均保留这两个证据边界。远程 workflow 为 `NOT_REMOTE_VERIFIED`，actionlint 为 `NOT_EXECUTED`。

## 调优阶段 10：浏览器认证通知闭环 - 2026-08-10

- 修复 WebSocket 握手 Principal 与通知 userId 不一致、CONNECT Principal 未传播到后续 SUBSCRIBE 的问题；服务端仅从已验证身份推导用户目的地。
- 修正 Playwright 运行编号和通知断言，避免小写任务编号及“标题/任务编号”混淆造成的假失败。
- 真实 Chromium acceptance 复验完成 `3 × 9/9`：登录、401/403、任务创建/更新、重复提交、登出失效、附件、STOMP `MESSAGE` 到通知中心，以及断线重连后的 HTTP 未读补拉均通过。
- 证据见 `docs/e2e-browser-report-2026-08-10.md`；结果仅代表本地单后端 Compose/simple broker，不代表生产 HA 或跨实例 WebSocket 广播。

## 调优阶段 8：确定性验收环境 - 2026-08-09

- 新增隔离的 `acceptance` Spring profile 和独立 `docker-compose.acceptance.yml`，使用专属命名卷，不删除现有开发卷。
- 新增仅在 `acceptance` profile 激活的验收管理员初始化器：凭据由环境变量注入，使用 BCrypt 动态生成密码哈希，可重复创建/对齐角色，不参与 dev/prod。
- Playwright 与性能工具统一读取 `TASKFLOW_ACCEPTANCE_*` 凭据变量，移除仓库内和脚本内的测试密码默认值。
- 新增 `scripts/acceptance-up.ps1`、`acceptance-check.ps1` 和 `acceptance-down.ps1`，自动验证健康、登录、`/api/auth/me`、任务列表、登出及旧会话失效。
- 新增 `docs/acceptance-environment.md`，明确 CI 注入方式、生产 profile 不创建测试账号和未验证项；本阶段不改变业务 API 契约。

## GitHub Actions exit code 126 修复 - 2026-08-09

- 修复 Linux runner 直接执行 `./mvnw` 的 exit code 126：`mvnw` Git mode 从 `100644` 修复为 `100755`，workflow 同时使用 `bash ./mvnw`，不再依赖 Windows 工作区的 executable bit。
- `actions/checkout`、`setup-java`、`setup-node` 升级到 Node 24 运行时版本线，artifact upload 升级到 Node 24 版本，消除 Node 20/deprecated setup-java v4 警告。
- integration-security artifact 改为 `if-no-files-found: ignore`；Maven 前置失败时不会把缺失覆盖率/依赖报告误判为新的根因。npm audit 固定使用官方 registry 并保留真实退出码说明。
- 修复第二个 CI 根因：`.mvn/maven.config` 不再提交 `F:\newinstall` Windows settings/repository 路径，改用跨平台 `.m2-local/repository`；GitHub Actions 清空 `MAVEN_ARGS/MAVEN_OPTS` 并使用 runner 临时 Maven 用户目录。

## 阶段 7 全量复验与最终评分 - 2026-08-09

- 重新执行后端默认回归 67/0/1，显式 Testcontainers verify 67/0/0，真实完成 8 条 Flyway 迁移；JaCoCo 行覆盖率 47.70%，门禁通过。
- 按 package-lock 执行前端 `npm install`，`npm run build` 通过；最终最大共享 chunk 747.99 KB，仍有 Vite 500 KB warning；项目无 lint/test script，未虚构执行结果。
- Compose 六服务均 healthy；精确重启 backend、frontend、MySQL、Redis、RabbitMQ、MinIO 后恢复 healthy，health 和 frontend 首页返回 200；未删除卷。
- Kind base/overlay Kustomize、backend/frontend/local edge rollout 和 backend Pod 删除恢复通过；本轮未把无 Ingress Controller 的 Kind 环境包装成生产 Ingress/HA。
- 阶段 5 故障脚本全量 stop/start 演练执行并生成 `docs/fault-injection-final-2026-08-09.json`；因没有注入认证 Token/数据库密码，认证和 DB 事实计数明确记录为未采集。
- 官方 npm audit 当前为 2 个 moderate、high/critical 0；OWASP Dependency-Check 实际运行但因 hosted suppressions 连接重置和依赖告警失败，未声称零漏洞。
- Playwright 最终执行为 1 失败、8 未运行（缺少 E2E 凭据）；同参数性能复测因缺少管理员密码安全失败，均不伪造为通过。
- 新增 `docs/final-optimization-summary-2026-08-09.md`，最终评分按严格证据调整为 80/100；同步验收报告、评分 JSON 和中文面试材料。

## 阶段 6 Kind 生产样式本地验证 - 2026-08-09

- 新增 `k8s/base` 与 `k8s/overlays/kind-production-like` 配置分层；overlay 使用 prod profile、只暴露 health 管理面，并且不覆盖已注入的 `taskflow-secret`。
- 新增 `taskflow-edge` 的 `/`、`/api`、`/ws` TLS 路由定义和 `scripts/prepare-kind-tls.ps1`；本地私钥保存在被忽略的 `runtime-secrets`，不提交仓库。
- 当前 Kind 没有 Ingress Controller，因此增加 namespace 内 `taskflow-local-edge` 仅用于本地 HTTPS/WSS 验证；HTTPS 首页和 `/api/health` 返回 200，WSS Upgrade 返回 101。
- 重新验证 backend 2 副本、frontend 1 副本、local edge rollout 和健康探针；不把 Kind 单节点结果包装成生产 HA。STOMP 认证/订阅/浏览器通知闭环仍未在本阶段通过。
- 详细证据见 `docs/kind-production-like-validation.md`，项目评分保持 82/100，生产就绪仍为否。

## 阶段 5 故障注入与恢复 - 2026-08-09

- 新增 `scripts/fault-injection.ps1`，默认计划模式，只允许对精确的 TaskFlow 容器执行 stop/start，禁止删除卷、清库和 `compose down`。
- Redis 短暂不可用真实演练通过：会话/Redis 索引期间不可用，恢复后恢复，Flyway V8 和核心计数不变。
- RabbitMQ 短暂停止/恢复真实演练通过；真实非法消息到 retry/DLQ/replay 因执行审批未完成，不包装为已通过。
- MinIO 停止期间附件上传真实返回 500，附件元数据进入 FAILED，MinIO 恢复后健康；对象列表扫描未完成。
- MySQL 停止、backend 重启和浏览器 WebSocket 重连本阶段未复验，项目评分按证据边界调整为 82/100，生产 HA 仍不通过。

## 阶段 4 性能与可观测性调优 - 2026-08-09

- 为 Dashboard、Login、Management 和 Task 路由增加懒加载；前端构建通过，旧入口 JS 1,156,965 bytes，新最大共享 chunk 747,991 bytes，下降约 35.38%，但仍保留 Vite 500 KB 提示，未声称总传输量下降。
- 复核任务列表、通知列表 SQL、批量负责人查询和现有索引；当前 EXPLAIN 不足以证明需要新增索引，因此没有添加 Flyway migration。
- 扩展运行时采集至 HTTP、HikariCP、Executor、JVM、RabbitMQ 和 Redis，并新增阶段4运行时、EXPLAIN 和对比报告。
- 同参数优化后压测因原管理员凭据不可用而阻塞；没有使用员工账号制造不可比的 QPS/p95/p99 结论，项目评分保持 81/100。

## 阶段 3 浏览器 E2E - 2026-08-09

- 新增 Playwright 9 场景、运行编号隔离夹具、失败截图/视频/trace 保留和真实 STOMP 浏览器观测；修正登录完成等待、Modal 提交定位、标题隔离，并让 WebSocket 后续 STOMP 帧恢复 CONNECT 认证 Principal。当前 Compose 浏览器实测为 4/9 通过，真实订阅仍待修复后复验，详见 `docs/e2e-browser-report-2026-08-09.md`。

## 认证遗留问题修复 - 2026-08-09

- 2026-08-09 阶段 2 工程治理：新增 JaCoCo HTML/XML 报告和 bundle/核心类覆盖率门禁，新增 fast-check 与 integration-security GitHub Actions，纳入 Testcontainers、Compose/Kustomize 静态校验、npm audit 和 OWASP Dependency-Check；真实扫描结果与网络/NVD 超时边界记录在 `docs/dependency-security-report.md`。
- 2026-08-09 阶段 1 安全基线收敛：按 dev/test/prod 分离敏感配置，生产缺失/弱 Secret fail-fast，收紧 prod Actuator/OpenAPI，增加 Spring Security/Nginx 基础安全 Header，明确不信任任意转发 Header，并将 `.env.example`/Kubernetes Secret 模板改为空值安全模板；保留 Bearer + STOMP 一致性，localStorage、TLS/WSS 和集中式密钥管理仍列为生产遗留风险。
- 参考 PriceSight 项目级加权验收范式，新增 TaskFlow 100 分制综合评估：79/100；本地工程基线有条件通过，生产发布不通过。评分维度覆盖需求核心、架构、安全、测试、评估可信度、运行集成、工程治理和文档交付。
- 新增 `docs/project-acceptance-score-2026-08-09.json` 机器可读评分结果，并将 P0/P1/P2 生产门禁、权重、证据和分层交付判定同步到全面验收报告。
- 阶段 0 冻结 79/100 调优基线：记录 Git HEAD、dirty 工作区、F 盘工具路径、端口/环境变量、Flyway V1–V8、测试和部署入口；新增阶段 1～7 调优待办矩阵。基线阶段未修改业务代码、数据库卷或 Kind 资源。
- JWT 增加唯一 `jti`，登录后在 Redis 保存带过期时间的活动会话标记。
- 新增后端 `POST /api/auth/logout`，HTTP 鉴权过滤器和 WebSocket CONNECT 均检查 Redis 会话，主动退出后旧会话被拒绝。
- 新增按登录名和来源地址哈希分组的 Redis 登录失败窗口，默认 10 次/60 秒，并增加统一 429 错误码。
- 前端退出流程先调用后端撤销接口，再清理本地 Token；服务端未确认时向用户提示。
- 新增会话、登出、限流和 JJWT jti 测试；默认回归为 64 项执行、0 失败，显式阶段 14 Testcontainers 测试另行通过，真实 Compose 旧 Token 失效和限流烟测通过。
- Testcontainers 1.21.4 已在当前 Docker Engine 29.6.2/API 1.55 环境完成 MySQL、Redis、RabbitMQ、MinIO 和 Flyway 集成烟测。
- 将 Gradle、npm、Maven 及 Testcontainers 的项目相关配置/缓存固定到 F 盘，避免项目工具继续在 C 盘生成非必要缓存。
- 补充本地 Kind `dev` 集群环境信息：Kubernetes v1.32.2、单节点 `dev-control-plane`、context `kind-dev`，状态 Ready；kind v0.27.0 位于 F 盘。另记录 kubectl 当前由 Docker Desktop 提供 v1.36.1，F 盘副本为 v1.32.0。
- 完成阶段 17 Kind 实机验收：后端 2 副本、前端 1 副本通过探针，前后端端口转发健康检查返回 HTTP 200；后端 Pod 删除后自动恢复，前后端滚动重启成功。该结果仅覆盖本地单节点应用层。
- 完成阶段 15 本地性能基线：固定 10 个部门、100 个用户、1000 个任务，20 并发、预热 10 秒、采样 60 秒；六个场景均 0 错误，报告写入 `docs/performance-baseline.json`，运行时指标写入 `docs/performance-runtime.json`，EXPLAIN 输出写入 `docs/performance-explain.txt`。数字仅代表当前 Windows 本机，不作为生产容量承诺。
- 将 Testcontainers 从 1.20.6 升级到 1.21.4，针对 Docker Engine 29.6.2/API 1.55 的兼容性问题进行环境修复。

## 项目全面验收 - 2026-08-09

- 新增项目全面验收与高维度评估报告，区分构建通过、功能通过、运行态证据、生产风险和未验证项。
- 重新执行后端回归测试：58 项执行、0 失败、1 项跳过；前端生产构建和 Compose 配置通过。
- 通过现有 Compose 完成六服务健康、核心认证/API、Flyway V8、重启保卷恢复烟测；未删除数据库或 Docker 卷。
- 初版报告曾记录阶段 3 的 Redis 会话、后端登出和 Token 主动撤销缺口；该缺口已由后续“认证遗留问题修复”条目补齐代码路径。
- 明确记录阶段 14 专用 Testcontainers 测试已通过，以及阶段 17 Kind 应用层实机验收和阶段 15 本地性能基线的证据边界。
- 同步修正认证、自动化测试和部署文档中的证据边界与当前验证数字。

## 阶段 19 - 面试和简历材料 - 2026-08-08

- 新增基于已验证代码整理的中文简历描述、30 秒和 2 分钟项目介绍，以及实习场景下的个人职责边界。
- 新增架构图、任务状态图、RBAC/数据范围图和提醒消息链路图。
- 新增 50 个项目深挖问题及参考回答、5 个困难问题、可如实量化指标和禁止/未验证表述清单。
- 明确区分个人贡献、团队协作成果和项目整体成果。

## Stage 18 - Security and quality review - 2026-08-08

- Hardened audit logging to avoid treating client-controlled `X-Forwarded-For` as authoritative.
- Added attachment content-signature checks for PDF, PNG, JPEG and NUL-byte rejection for text files.
- Added regression coverage for spoofed forwarded addresses and mismatched binary attachment content.
- Removed a copyable administrator password from authentication documentation.
- Documented unresolved production risks including default local secrets, localStorage token exposure, missing login rate limiting, and single-instance local infrastructure.

## Stage 17 - Local Kubernetes deployment - 2026-08-08

- Added Namespace, ConfigMap, Secret, frontend/backend Deployments, and ClusterIP Services under `k8s/`.
- Configured two backend replicas, RollingUpdate strategy, startup/readiness/liveness probes, and resource requests/limits.
- Added a PowerShell renderer/deployer with optional Kind and Minikube local-image loading.
- Documented the local learning topology where Kubernetes hosts the application layer and Docker Compose continues to provide middleware.
- Initial implementation did not claim live Pod recovery validation; the follow-up local Kind `dev` verification completed deployment, probes, Pod recovery, and rolling restart checks.

## Stage 16 - Docker Compose deployment - 2026-08-07

- Added multi-stage Java 17 backend and Node/Nginx frontend Dockerfiles.
- Extended Compose from infrastructure-only to a six-service application deployment with internal service addresses, health checks, restart policies, environment-driven ports and credentials, and persistent named volumes.
- Added safe PowerShell initialization and cleanup scripts; volume removal requires explicit confirmation flags.
- Documented local development mode versus full-container mode.

## Stage 15 - Query optimization and performance harness - 2026-08-07

- Added standard-library Python tooling for configurable department/user/task data preparation and login, task list, task detail, task creation, state update, and notification list benchmark scenarios.
- Added EXPLAIN statements and a PowerShell runtime collector for Actuator, JVM, and Redis observations.
- Exposed authenticated Actuator metrics for connection-pool, executor, JVM, and GC inspection.
- Added a reproducible local baseline and runtime/EXPLAIN evidence without changing indexes based only on one machine's result; production capacity remains unverified.

## Stage 14 - Automated testing - 2026-08-07

- Added expired/malformed JWT and invalid-credential authentication regression tests.
- Added an opt-in Testcontainers smoke test covering fresh Flyway migration on MySQL and availability of Redis, RabbitMQ, and MinIO.
- Documented the existing regression categories for RBAC, data scope, state transitions, optimistic concurrency, notification idempotency, reminder recovery/cancellation, attachment authorization, and audit consistency.
- Kept the default Maven test command Docker-independent; container tests require `-Dtaskflow.integration=true`.

## Stage 13 - Frontend foundation - 2026-08-07

- Added React login/logout, persisted access tokens, current-user loading, automatic 401 sign-out, and unified API error handling.
- Added project dashboard, task list/detail/create/edit/status/assignment flows, comments, attachments, notification center integration, and duplicate-submit protection.
- Added permission-aware user, role, and department management pages.

## Stage 12 - Audit logs and observability - 2026-08-07

- Added `AuditAction` AOP auditing for login, permission/user changes, task key operations, attachment deletion, and notification dead-letter replay.
- Added protected audit-log queries with `audit:view`, safe metadata-only details, trace/operator/resource/result fields, slow-request WARN logging, RabbitMQ messageId MDC correlation, and Actuator health probes.
- Added stage 12 unit coverage for successful/failed audit recording and sensitive-value exclusion.

## Stage 11 - WebSocket notification center - 2026-08-07

### Added

- Added STOMP over WebSocket endpoint `/ws/notifications` with JWT authentication in the STOMP `CONNECT` frame.
- Added server-derived user destinations, multi-device session tracking, and disconnect cleanup.
- Added after-commit notification push events with failure isolation from notification/task transactions.
- Added a lightweight frontend notification center with HTTP unread backfill, read/all-read actions, bounded reconnects, and Vite WebSocket proxying.
- Added WebSocket authentication and push-target unit tests.

### Fixed

- Compare JWT numeric `uid` by value instead of Java wrapper type, so valid small user IDs parsed as `Integer` authenticate correctly against database `Long` IDs.
- Wrap immutable STOMP headers and return a new authenticated message so `CONNECT` authentication works with Spring's immutable message headers.

### Not included

- The full frontend login workflow remains a later stage; the notification center reads an existing access token from local storage.

## Documentation - complete phased development prompt - 2026-08-07

### Added

- Archived the complete user-provided Codex phased development prompt at `docs/codex-phased-development-prompt.md`.
- Added the prompt to the README documentation index for future stage planning and acceptance checks.

## Stage 10 - RabbitMQ asynchronous notifications - 2026-08-07

### Added

- Added V8 notification source-message idempotency, dead-letter records, and notification/dead-letter permissions.
- Added reminder and task-status event messages with event type, message ID, trace ID propagation, and after-commit task-status publishing.
- Added manual-Ack notification consumer, bounded TTL retry queue, dead-letter queue, and explicit failure-reason recording.
- Added idempotent notification creation for task assignees and task-status recipients.
- Added current-user notification pagination, unread count, mark-read, mark-all-read, dead-letter viewing, and administrator replay APIs.
- Added publisher confirm, mandatory return, and correlation-data configuration for RabbitMQ producers.

### Not included

- WebSocket real-time notification delivery remains in stage 11.

## Stage 9 - reminder plans - 2026-08-07

### Added

- Added persistent due-soon and overdue reminder plan orchestration using the existing `reminder_plan` table.
- Added deadline and terminal-task synchronization, including cancellation of stale plans after deadline changes.
- Added Redis ZSet indexing, low-frequency database rebuild, and a TTL-based distributed scan lock.
- Added RabbitMQ exchange/queue declaration and stable plan-id message publishing; consumers remain in stage 10.
- Added version-conditional emitted/failed state transitions and reminder scheduling tests.

## Stage 8 - comments and attachments - 2026-08-07

### Added

- Added V7 stable comment and attachment permissions without changing the existing V1 tables.
- Added task comment create/page APIs and backend system-event comment support.
- Added MinIO-backed attachment upload, metadata page, streaming download, presigned URL, and delete APIs.
- Added file size/type/extension validation, filename sanitization, unpredictable object keys, SHA-256 metadata, task-scope checks, and conditional attachment status transitions.
- Added upload/delete compensation paths for MySQL and MinIO failures; no unbounded retry loop is introduced.
- Added stage 8 documentation and unit tests for permissions, validation, path isolation, and storage failure handling.

## Stage 7 - task state machine and concurrency - 2026-08-07

### Added

- Added old-status-plus-version conditional updates for task commands to prevent concurrent state overwrites.
- Made transfer consume a task version and keep assignment changes and operation logs in one transaction.
- Added a `complete` compatibility endpoint, explicit `COMMON_409` duplicate/concurrency semantics, and state concurrency tests.
- Added stage 7 state-machine and concurrency documentation.

## Stage 6 - task creation, query, and assignment - 2026-08-07

### Added

- Added V6 `task:update` and `task:delete` permissions for draft maintenance.
- Added draft edit and logical-delete APIs with creator/permission checks and optimistic locking.
- Added title, status, priority, assignee, creator, department, project, due-time, and creation-time filters.
- Reworked paginated task responses to batch-load all assignees in one query and avoid page-level N+1 queries.
- Added stage6 migration and task service tests for draft rules and batch loading.

### Verified

- Full backend test suite passed with 23 tests and no failures.
- MySQL 8.4 applied Flyway V6 successfully; representative task queries used the existing creator, project, and assignee indexes in `EXPLAIN`.

## Stage 5 - project and task lifecycle - 2026-08-07

### Added

- Added V5 project/task permissions and built-in role assignments.
- Added project creation, scoped project listing, and project member management APIs.
- Added task creation, scoped pagination/detail queries, primary/collaborator assignment, and transfer APIs.
- Added explicit task lifecycle commands: submit, accept, submit review, approve, reject, start, cancel, and archive.
- Added task state-machine tests, version-based optimistic locking, and transactional operation logs with before/after status and Trace ID.

### Not included

- Comments, attachments, notifications, reminder scheduling, MQ consumers, and MinIO integration remain outside this stage.

## Stage 4 - role, organization, and data-scope management - 2026-08-07

### Added

- Added V4 management permissions, built-in manager roles, and role data-scope seeds.
- Added role list/create/update APIs with stable permission-code validation and transactional relation replacement.
- Added department create/update APIs and user create/status/role-assignment APIs with Jakarta Validation and optimistic locking.
- Applied `SELF`, `DEPARTMENT`, `DEPARTMENT_AND_CHILDREN`, and `ALL` scope resolution to the user pagination query; project scope remains reserved for project/task queries.
- Added stage4 migration and data-scope unit tests.

### Not included

- Task/project business services, project data-scope query adapters, MQ consumers, and file storage remain outside this stage.

## Stage 3 - authentication and authorization foundations - 2026-08-06

### Added

- JWT login with BCrypt password verification and stateless Spring Security filtering.
- Current-user endpoint, active user/role/permission loading, and backend `@PreAuthorize` checks.
- User pagination and active department tree read APIs.
- Flyway V3 stable permission codes, built-in roles, and role-permission assignments.
- Environment-controlled, idempotent development administrator bootstrap without plaintext password seeds.
- Authentication, JWT, controller, service, and V3 migration tests.

### Verified

- Fresh MySQL database reached schema version 3.
- Local administrator login returned HTTP 200 and a JWT.

## Stage 2 - backend foundations - 2026-08-06

### Added

- Unified `ApiResponse`, stable `BusinessErrorCode`, `BusinessException`, and `GlobalExceptionHandler`.
- Trace ID filter/context with MDC and response-header propagation.
- MyBatis-Plus pagination, logical deletion, optimistic locking, automatic timestamps, and base audit fields.
- Optional Springdoc OpenAPI configuration.
- Controller, service, and migration-structure tests for the stage acceptance criteria.

### Verified

- Fresh MySQL 8.4 database applied Flyway V1 and V2 successfully and reached schema version 2; the backend health endpoint returned HTTP 200 with a Trace ID.

## [阶段 1] - 2026-08-06

### Added

- 增加 Flyway V1 初始化迁移，创建组织、权限、项目、任务、评论、附件、通知、提醒和审计表；
- 增加唯一约束、外键、状态检查约束、乐观锁 `version` 字段和基于查询场景的联合索引；
- 增加数据库结构静态测试；
- 补充角色边界、数据范围、任务状态机、ER 图和消息/提醒边界文档。

### Not included

- 未实现登录、Token、RBAC 接口、数据权限查询拦截、任务应用服务和 MQ 消费者。
- 真实 MySQL 空库迁移已在 MySQL 8.4 容器中验证通过；初次验证期间记录的 Docker 镜像下载问题已恢复。

## [阶段 0] - 2026-08-06

### Added

- 建立 Spring Boot 3.4.8 后端基础工程和 `/api/health` 健康检查接口；
- 建立 React + TypeScript + Vite + Ant Design 前端基础页面；
- 增加 MySQL、Redis、RabbitMQ、MinIO 的 Docker Compose 开发基础设施及健康检查；
- 增加 `.env.example`、README 和阶段 0 文档；
- 修正 Maven Wrapper 在 Windows 普通用户目录下的兼容性问题。
- 将 Maven 用户配置、Wrapper 发行版和本地依赖仓库迁移到 `F:\projects_2027\taskflow-platform\maven-user`，并更新当前用户的 Maven/Java 环境变量。

### Not included

- 未实现数据库表、Flyway 迁移、用户认证、RBAC、任务业务和消息业务。
## TaskFlow Platform - 阶段 9 浏览器认证 Token 存储安全化 - 2026-08-10

- 浏览器登录改为 profile 控制的 HttpOnly `TASKFLOW_ACCESS` Cookie；保留 JWT、Redis active-session、过期、登出撤销、角色权限和 Bearer 兼容接口。
- 启用 Spring Security CSRF：React 通过 `/api/auth/csrf` 获取 token 并提交 `X-XSRF-TOKEN`；dev/test/acceptance 支持本地 HTTP，prod Cookie 强制 `Secure=true`。
- WebSocket 同源握手复用 Cookie，STOMP CONNECT 不再发送 JWT，也不把 JWT 放入 URL；Bearer STOMP 兼容模式保留。
- 增加 Cookie、CSRF、Bearer 优先级、生产 Secure 属性和 Cookie WebSocket 会话测试；完整后端回归 75/0/1，阶段9 acceptance Cookie/CSRF smoke 通过。
- 正式 React 不再将 JWT 写入或读取 `localStorage`；Playwright 登录、401、403 和任务真实写链路通过，重复提交与后续通知场景仍保留失败证据，未宣称完整 E2E 通过。
- Stage 11.5B-E2E（2026-08-11）：使用重新打包的 Spring Boot 3.5.16 acceptance 镜像完成健康、Cookie/CSRF 和真实 Chromium 回归；direct `8/9 → 9/9`、Nginx proxy `8/9 → 9/9`。两条路径首轮均出现通知已落库但业务 STOMP MESSAGE 未到达的偶发证据，保留 trace/screenshot/video，Stage 11.5B 继续为 `PARTIAL_PENDING_BROWSER_E2E`，评分保持 85/100，Stage 13 未开始。
- Stage 11.5B-E2E-F（2026-08-11）：新增 acceptance-only 通知 C1-C3 diagnostics 与真实 client outbound channel 观测；Playwright 定向通知 direct/proxy 均 10/10，完整浏览器 E2E direct/proxy 均连续 2×9/9。原 `8/9 → 9/9` 偶发记录保留为历史证据，当前按 `TEST_OBSERVATION_RACE / SERVER_LOSS_NOT_REPRODUCED` 关闭 Stage 11.5B，评分暂保持 85/100，未启动 Stage 13。
- P1 Nginx STOMP proxy stability revalidation（2026-08-14）：独立真实 Chromium 复验 direct/proxy 定向各 20/20，完整场景 direct/proxy 各连续 2×19/19，四轮失败率 0/76。修正 C6 诊断字段的换行匹配；未修改 Nginx 或业务推送语义。C4 服务器物理 WebSocket transport 边界因 acceptance 镜像未重新部署诊断类而保持 NOT_ESTABLISHED，P1 为 OPEN_WITH_DOCUMENTED_LIMIT，正式评分保持 83/100。
