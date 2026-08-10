# TaskFlow Platform

## 阶段 11.6 WebSocket 代理链路

2026-08-10 的本地 acceptance 验证已完成 Nginx `/ws/notifications` 代理闭环：Chromium → frontend Nginx → backend → STOMP CONNECT/CONNECTED/SUBSCRIBE → 真实 MESSAGE → 页面通知。Nginx proxy 连续 3 轮 9/9，backend direct 回归 9/9。根因是前端在 CONNECTED 后过早宣告订阅就绪，代理时序放大了竞态；现通过已认证的 `/app/notifications/ready` 应用消息确认 SUBSCRIBE 已处理。详情见 [阶段 11.6 浏览器 E2E 报告](docs/e2e-browser-report-2026-08-10-stage11-6.md)。

该证据仅覆盖本地 Docker Compose 单节点代理链路，不等同于生产 Ingress、跨节点 HA 或云负载均衡验证。

## Stage 13 progress

## Stage 14 progress

Stage 14 adds regression coverage for authentication/token rejection, RBAC and data scope, task state transitions, optimistic concurrency, notification idempotency, reminder cancellation/recovery, attachment authorization, and audit consistency. An opt-in Testcontainers smoke test is available for fresh MySQL/Flyway, Redis, RabbitMQ, and MinIO infrastructure.

Stage 15 adds repeatable performance data preparation, six HTTP benchmark scenarios, protected runtime metric collection, and EXPLAIN templates. Stage 4 adds route-level frontend lazy loading and broader HTTP/HikariCP/RabbitMQ/Redis runtime evidence. A local baseline is recorded under `docs/`; numbers remain environment-specific and are not production capacity claims. The comparable post-optimization replay is currently blocked because the original administrator credential is unavailable; no substitute employee-account result is treated as a performance comparison.

Stage 16 adds backend/frontend Dockerfiles, a full six-service Compose deployment, health checks, persistent volumes, environment-driven container addresses, and safe initialization/cleanup scripts.

Stage 17 adds local Kubernetes manifests for the frontend and a two-replica backend Deployment, with ConfigMap/Secret separation, rolling updates, startup/readiness/liveness probes, resource requests/limits, and a safe local deployment script. MySQL, Redis, RabbitMQ, and MinIO remain supplied by Docker Compose for this learning phase.

Stage 18 adds a security and quality review, hardens audit source-address handling and attachment content validation, and records verified findings and unresolved production risks.

阶段 5 新增非破坏性故障注入脚本和本地证据：Redis、RabbitMQ、MinIO 短暂停止/恢复，以及 MinIO 上传失败后的 FAILED 元数据状态。RabbitMQ 真实非法消息到重试/DLQ/replay、MySQL 本阶段保卷重启和浏览器 WebSocket 重连仍需单独补验；本地单容器恢复不代表生产 HA。

阶段 19 新增基于已验证代码整理的中文面试和简历材料，包括架构图、状态与消息时序图、RBAC 和数据范围说明、50 个项目深挖问题及参考回答、个人贡献边界，以及未经进一步验证不得使用的项目表述。

阶段 6 新增 Kind `kind-production-like` overlay、`taskflow.local` 本地 TLS 证书脚本、`/`/`/api`/`/ws` 路由定义和 namespace 内 HTTPS/WSS 验证 edge。当前集群没有 Ingress Controller，因此仅计为生产样式静态配置和本地 TLS/WSS 握手证据，不计为真实生产 Ingress 或生产 HA。

阶段 8 在阶段 7 基础上建立了确定性 acceptance 环境；阶段 9 复验后默认 Maven 为 75/0/1，显式 Testcontainers verify 为 75/0/0，Cookie/CSRF acceptance smoke 通过。阶段 10 在后端直连路径完成真实 Chromium `3 × 9/9`，包括 STOMP MESSAGE 到通知中心、断线重连和 HTTP 补拉；阶段 11.6 已完成 Nginx `/ws` 代理路径真实 MESSAGE 闭环。阶段 11.5/12.4 已将 npm audit 清零，并将 OSV-Scanner 设为主依赖漏洞门禁；OWASP/NVD 保留为 `SUPPLEMENTAL_NVD_REMOTE_BLOCKED`，不再因当前外部 NVD 不可达阻塞主 CI。Maven 依赖风险、生产 TLS、外部密钥轮换、真实 Ingress、跨实例 WebSocket 广播和同参数性能复测仍未闭环，生产发布仍不通过。详见[阶段 11.6 浏览器复验](docs/e2e-browser-report-2026-08-10-stage11-6.md)、[依赖漏洞治理与 CI 门禁记录](docs/dependency-security-report.md)、[依赖漏洞归因](docs/dependency-vulnerability-triage-2026-08-10.md)、[最终复验摘要](docs/final-optimization-summary-2026-08-09.md)和[确定性验收环境](docs/acceptance-environment.md)。

The backend now includes task draft maintenance, filtered and paginated task queries, primary/collaborator assignment, scoped detail access, batch assignee loading, task operation logs, a fixed task state machine, optimistic concurrency control using old-status-plus-version conditional updates, task comments, MinIO-backed attachment metadata workflows, persistent reminder plans, Redis ZSet scheduling, distributed scanning locks, RabbitMQ reminder publishing, idempotent notification consumers, bounded retries, dead-letter compensation, HTTP notification query APIs, and STOMP over WebSocket user-destination push. Flyway V1 through V8 are designed for a fresh MySQL database.

企业任务协同与流程管理平台，面向学习和校招面试准备，采用模块化单体架构逐阶段实现。

## 当前进度

当前阶段 19 已完成登录、Redis 会话标记、后端登出撤销、登录失败限流、前端退出体验、HttpOnly Cookie/CSRF、Token 兼容接口、任务与项目基础流程、评论、附件、通知中心、用户/角色/部门管理、统一错误处理、自动化测试、性能工具、全容器部署、本地 Kubernetes 应用层清单、安全质量审查、阶段5部分故障恢复演练、阶段7最终复验、阶段10真实浏览器通知闭环和基于已验证代码的面试简历材料。阶段 9 已移除正式 React 流程对 localStorage JWT 的读写；生产 TLS、外部密钥轮换、真实 Ingress、跨实例 WebSocket 广播和 HA 仍未完成。

2026-08-11 阶段 12.4 当前结论：npm 官方 audit 为 moderate/high/critical 全部 0；OSV-Scanner v2.5.0 已真实扫描 Maven 27 packages 与 frontend 220 packages，发现 70 个 Maven 漏洞并正确阻断主门禁；Maven OWASP Dependency-Check 保留为 `SUPPLEMENTAL_NVD_REMOTE_BLOCKED`，本地报告仍有真实高危记录，不能视为依赖零漏洞。项目可在本地 Compose 和隔离 acceptance Compose 环境运行和演示；上述结果仍不等同于生产容量、生产 Ingress 或生产高可用，项目暂不判定为生产就绪。详见[依赖漏洞治理与 CI 门禁记录](docs/dependency-security-report.md)、[依赖漏洞归因](docs/dependency-vulnerability-triage-2026-08-10.md)和[项目全面验收与高维度评估报告](docs/project-acceptance-report-2026-08-09.md)。

2026-08-11 Stage 11.5B 已建立依赖漏洞基线与逐包归因，并提交一次仅变更 Spring Boot parent 的 3.4.8→3.5.16 小批候选；完整 Maven、OSV、Testcontainers 和浏览器回归尚未取得本次提交后的证据，因此评分仍为 85/100，Stage 13 不启动。详见[Stage 11.5B remediation](docs/dependency-vulnerability-remediation-2026-08-11.md)和[Spring Boot 3.5.16 impact](docs/spring-boot-3-5-upgrade-impact.md)。

参考 PriceSight 项目采用的加权验收方法，本项目阶段 12.4 当前仍建议 **85/100**：本地工程基线有条件通过，可用于学习、演示和面试；生产发布不通过。npm moderate 已清零，OSV-Scanner 主门禁的第一次远程结果待验证；OWASP/NVD 仍是 supplemental 外部访问受限证据。评分未因新增扫描配置机械上调。评分明细见[结构化评分结果](docs/project-acceptance-score-2026-08-10.json)。

## 技术栈

- 后端：Java 17、Spring Boot 3.4.x、Spring Security、Maven Wrapper；
- 前端：React、TypeScript、Vite、React Router、Axios、Ant Design；
- 基础设施：MySQL 8、Redis 7、RabbitMQ 3、MinIO；
- 运行方式：Windows 11、Docker Desktop、Docker Compose；阶段 17 增加 Kubernetes 本地应用层部署。

## 目录

```text
backend/
├─ src/main/java/yvon/backend/       # Spring Boot 后端
├─ src/test/                         # 后端测试
├─ frontend/                         # React + Vite 前端
├─ docker-compose.yml                 # 阶段 16 全容器部署
├─ k8s/                               # 阶段 17 Kubernetes 应用层清单
├─ .env.example                       # 本地环境变量模板
└─ docs/                              # 持续维护的项目文档
```

## 启动全容器模式

在仓库根目录执行：

```powershell
.\scripts\init-compose.ps1 -Rebuild
```

首次执行会从 `.env.example` 创建 `.env`；该模板不再放入可直接使用的密码或 JWT 值，请逐项填写本地开发 Secret 后再启动。生产环境必须使用 `SPRING_PROFILES_ACTIVE=prod` 并提供全部必需 Secret，缺失或弱默认值会 fail-fast。全容器模式包含前端、后端、MySQL、Redis、RabbitMQ、MinIO，默认前端地址为 `http://localhost:5173`。

安全边界：dev/test/acceptance 浏览器使用 HttpOnly `TASKFLOW_ACCESS` Cookie + CSRF Header，prod 默认 `Secure=true`、`SameSite=Lax`；登录 JSON 仍保留 Bearer Token 兼容字段供脚本和集成测试使用，但正式 React 不把 JWT 写入或读取 `localStorage`。prod 默认关闭 Swagger/OpenAPI，仅暴露 Actuator 健康探针，并要求显式的 JWT、数据库、RabbitMQ、MinIO 和 bootstrap admin Secret。WebSocket 同源握手使用 Cookie，STOMP CONNECT 不把 JWT 放入 URL。生产部署仍必须由可信反向代理提供 HTTPS/WSS，并在代理边界明确配置；应用默认不信任任意 `X-Forwarded-*`。详见[安全与权限边界](docs/security.md)和[部署说明](docs/deployment.md)。

Kind 生产样式本地验证：

```powershell
.\scripts\prepare-kind-tls.ps1 -Force
.\scripts\deploy-k8s.ps1 -Overlay kind-production-like
```

完整证据和边界见[Kind 生产样式本地验证记录](docs/kind-production-like-validation.md)。

容器内部使用服务名连接：后端连接 `mysql:3306`、`redis:6379`、`rabbitmq:5672`、`minio:9000`；前端 Nginx 代理到 `backend:8080`。宿主机端口默认映射为 MySQL `3307`、Redis `6380`、RabbitMQ `5673`、管理台 `15673`、MinIO `9000/9001`、后端 `8080`、前端 `5173`。

停止但保留数据卷：

```powershell
.\scripts\cleanup-compose.ps1
```

删除卷必须显式确认：

```powershell
.\scripts\cleanup-compose.ps1 -RemoveVolumes -ConfirmDataLoss
```

## 本地开发模式

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

另开终端运行前端：

```powershell
Set-Location frontend
npm run dev
```

本地模式下 Vite 将 `/api` 和 `/ws` 代理到宿主机 `localhost:8080`。仅启动基础设施时可执行 `docker compose up -d mysql redis rabbitmq minio`。

本项目工具缓存统一放在 F 盘：Maven 用户目录为 `F:\newinstall\maven-user`、本地仓库为 `F:\newinstall\maven-repository`，Gradle 用户目录为 `F:\newinstall\gradle-user-home`，npm 缓存为 `F:\newinstall\npm-cache`。提交到仓库的 `.mvn/maven.config` 使用跨平台的项目相对仓库 `.m2-local/repository`，避免把 Windows 路径带入 GitHub Linux；本机新的 PowerShell 或 IDE 会话仍可通过 `MAVEN_USER_HOME`、`MAVEN_ARGS`、`GRADLE_USER_HOME`、`NPM_CONFIG_CACHE` 和 `JAVA_HOME` 指向 F 盘。GitHub Actions 会清空 Windows 专用 Maven 参数并使用 runner 临时目录。

Testcontainers 配置位于项目内 `src/test/resources/testcontainers.properties`，不依赖用户目录下的 C 盘配置；Docker Desktop 本体配置仍由 Docker 管理，未迁移其必要的 `.docker` 用户目录。

健康检查：<http://localhost:8080/api/health> 或 <http://localhost:8080/actuator/health>。

## 执行数据库迁移

先启动 MySQL 并准备本地配置，再显式开启 Flyway：

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3307/taskflow?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME = "taskflow"
$env:DB_PASSWORD = "change-me-in-local-config"
$env:FLYWAY_ENABLED = "true"
.\mvnw.cmd spring-boot:run
```

迁移脚本位于 `src/main/resources/db/migration/`，阶段10新增 `V8__add_notification_delivery_and_permissions.sql`，为通知幂等和死信补偿增加结构与权限。附件和提醒默认关闭；启用提醒时需配置 Redis、RabbitMQ。无数据库时默认关闭 Flyway，便于运行基础测试。

## 启动前端

```powershell
Set-Location frontend
npm install
npm run dev
```

前端地址：<http://localhost:5173>。`/api` 请求在开发服务器中代理到后端 `8080` 端口。

## 验证命令

```powershell
.\mvnw.cmd test
Set-Location frontend
npm run typecheck
npm run build
$env:TASKFLOW_ACCEPTANCE_BASE_URL = "http://127.0.0.1:15173"
$env:TASKFLOW_ACCEPTANCE_ADMIN_USERNAME = "<通过安全方式注入的验收管理员用户名>"
$env:TASKFLOW_ACCEPTANCE_ADMIN_PASSWORD = "<通过安全方式注入的验收管理员密码>"
$env:TASKFLOW_ACCEPTANCE_TEST_USER_PASSWORD = "<通过安全方式注入的本次运行测试密码>"
npm run e2e
$env:npm_config_registry = "https://registry.npmjs.org"
npm run audit:ci
Set-Location ..
docker compose config --quiet
F:\newinstall\kubectl.exe kustomize k8s
```

覆盖率和集成门禁：

```powershell
.\mvnw.cmd verify
.\mvnw.cmd "-Dtaskflow.integration=true" verify
.\mvnw.cmd "-Dtaskflow.integration=true" "-Dtest=Stage12ReliabilityContainerTest" test
```

JaCoCo 报告位于 `target/site/jacoco/`。Windows PowerShell 中必须给 `-Dtaskflow.integration=true` 加引号，避免被 Maven 误解析为 `.integration=true` 生命周期阶段；GitHub Linux runner 使用 `bash ./mvnw`，且 `mvnw` 已标记为 Unix executable，避免 exit code 126。GitHub Actions 分为 `fast-check` 和 `integration-security`：前者阻断快速回归、前端构建及 Compose/Kustomize 静态错误；后者执行 Testcontainers、覆盖率、npm audit 和 OWASP supplemental；同一 workflow 的官方 OSV-Scanner reusable job 是主 Maven/npm 依赖漏洞门禁，漏洞或扫描基础设施失败均阻断。moderate 不单独阻断，但本轮 npm audit 已为 0。OWASP/NVD 远程自动化标记为 `SUPPLEMENTAL_NVD_REMOTE_BLOCKED`，不能把本地历史报告或 YAML 静态解析当成实时扫描通过。本机未安装 actionlint，记为 `NOT_EXECUTED`。Actions 已迁移到 Node 24 运行时版本线。

浏览器 E2E 使用 Playwright，覆盖登录、无 localStorage JWT、401/403、任务真实写入、重复提交保护、登出失效、附件入口、真实 STOMP 通知和断线补拉。阶段 8 起，Playwright 和性能工具统一读取 `TASKFLOW_ACCEPTANCE_ADMIN_USERNAME`、`TASKFLOW_ACCEPTANCE_ADMIN_PASSWORD`、`TASKFLOW_ACCEPTANCE_TEST_USER_PASSWORD`，不再猜测现有数据库管理员密码。测试密码只通过当前终端或 CI Secret 注入，不写入仓库；失败时 Playwright 在 `frontend/test-results/` 保留截图、视频或 trace。阶段 10 acceptance 已完成真实 Chromium `3 × 9/9`；完整运行记录见[阶段 10 浏览器复验](docs/e2e-browser-report-2026-08-10.md)。完整隔离环境见[确定性验收环境](docs/acceptance-environment.md)。

## 确定性验收环境

阶段 8 提供独立的六服务 acceptance Compose 栈，使用独立命名卷和 `acceptance` profile。它只在启动时从环境变量创建/重置验收管理员，不会修改 dev/prod 的正式管理员，也不会删除卷：

```powershell
# 先在当前终端注入 .env.example 中列出的 TASKFLOW_ACCEPTANCE_* 值；不要把值写入脚本或 Git。
.\scripts\acceptance-up.ps1
.\scripts\acceptance-check.ps1
Set-Location frontend
npm run e2e
Set-Location ..
python tools/performance/performance_harness.py prepare --output docs/performance-acceptance-prepare.json
```

停止验收容器但保留数据卷：

```powershell
.\scripts\acceptance-down.ps1
```

验收栈、所需变量、CI 注入方式和安全边界见[确定性验收环境说明](docs/acceptance-environment.md)。

## 文档

- [完整 Codex 分阶段开发 Prompt](docs/codex-phased-development-prompt.md)
- [阶段 0 部署说明](docs/deployment.md)
- [需求与角色](docs/requirements.md)
- [数据库设计](docs/database.md)
- [安全与数据范围](docs/security.md)
- [阶段 4 角色与组织管理](docs/stage4-management.md)
- [阶段 5 项目与任务管理](docs/stage5-project-task.md)
- [阶段 6 任务查询与分配](docs/stage6-task-query.md)
- [阶段 7 状态机与并发控制](docs/stage7-concurrency.md)
- [阶段 8 评论与附件](docs/stage8-comments-attachments.md)
- [阶段 9 提醒计划](docs/stage9-reminders.md)
- [阶段 10 RabbitMQ 异步通知](docs/stage10-notifications.md)
- [阶段 11 WebSocket 通知中心](docs/stage11-websocket-notifications.md)
- [阶段 12 审计日志和可观测性](docs/stage12-audit-observability.md)
- [阶段 13 前端基础功能](docs/stage13-frontend.md)
- [阶段 14 自动化测试](docs/stage14-testing.md)
- [阶段 3 浏览器 E2E 验收记录](docs/e2e-browser-report-2026-08-09.md)
- [阶段 15 性能工具](docs/performance.md)
- [阶段 4 性能优化对比](docs/performance-comparison-2026-08.md)
- [阶段 5 故障注入与恢复验收](docs/fault-injection-2026-08-09.md)
- [阶段 12 Testcontainers 故障注入自动化验收](docs/fault-injection-acceptance-2026-08-10.md)
- [阶段 16 Docker Compose 部署](docs/deployment.md)
- [阶段 17 Kubernetes 本地部署](docs/k8s-local.md)
- [阶段 18 安全和质量审查](docs/stage18-security-quality.md)
- [阶段 2 依赖安全与质量门禁记录](docs/dependency-security-report.md)
- [阶段 19 面试和简历材料](docs/stage19-interview-materials.md)
- [项目全面验收与高维度评估报告（2026-08-09）](docs/project-acceptance-report-2026-08-09.md)
- [确定性 acceptance 验收环境](docs/acceptance-environment.md)
- [项目级加权评分（2026-08-09）](docs/project-acceptance-score-2026-08-09.json)
- [调优基线与阶段1～7账本（2026-08-09）](docs/optimization-baseline-2026-08-09.md)
- [任务状态机](docs/task-state-machine.md)
- [消息与提醒边界](docs/message-flow.md)
- [测试说明](docs/testing.md)
- [架构说明](docs/architecture.md)
- [阶段学习笔记](docs/learning-notes.md)
- [面试笔记](docs/interview-notes.md)
