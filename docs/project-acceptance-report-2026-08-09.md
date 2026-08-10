# TaskFlow Platform 项目全面验收与高维度评估报告（2026-08-09）

## 1. 验收结论

本报告是对阶段 0 至阶段 19 的一次独立复核，重点检查的不只是“代码是否存在”，还包括：需求覆盖、架构边界、数据一致性、外部依赖、运行恢复、安全风险、测试证据、部署证据和文档可信度。本版增加参考 PriceSight 项目采用的项目级加权评分，但评分不替代生产发布门禁。

最终结论（阶段 10 增量复验后）：

> **阶段 10 复验后项目级评分建议：85/100。结论：本地工程基线有条件通过，可用于学习、演示和面试；生产交付仍不通过。**

阶段 9 已完成浏览器认证 Token 存储安全化：正式 React 不再读写 JWT `localStorage`，浏览器使用 HttpOnly Cookie + CSRF，WebSocket 同源握手复用 Cookie；Bearer 仅作为兼容客户端模式保留。阶段 10 在同一隔离 acceptance Compose 环境完成真实 Chromium `3 × 9/9`，包含真实 STOMP `MESSAGE` 到通知中心、断线重连和 HTTP 未读补拉。该证据提升了需求覆盖、测试可信度和运行集成评分，但不改变生产 HA、Ingress/TLS 或跨实例广播结论。

主要原因不是基础功能无法运行；本轮已完成认证、Compose、Kind 应用层、性能基线和部分基础设施故障恢复的本地证据，但仍有以下重要边界：

1. Redis 会话、后端登出、HTTP/WebSocket 会话撤销和登录失败限流已实现；真实 Compose“登录-登出-旧 Token 失效”和限流烟测均通过。
2. 阶段 14 的 Testcontainers 专用测试已通过；Testcontainers 1.21.4 成功启动 MySQL、Redis、RabbitMQ、MinIO，并完成全新 MySQL 的 8 条 Flyway 迁移。
3. 已在 Kind 本地集群 `dev`/`kind-dev` 完成前后端 Deployment 调度、探针、滚动更新、Pod 故障恢复和 Service 端口转发健康检查验收。
4. 阶段 15 已生成固定参数下的性能、运行时指标和 EXPLAIN 报告；阶段 4 完成了前端路由懒加载和新的运行时/EXPLAIN 采集，阶段 8 已证明性能工具可读取 acceptance 凭据并准备数据，但同参数性能复测尚未执行；这些数字只代表当前 Windows 11 本机，不代表生产容量。
5. 阶段 5 真实完成 Redis、RabbitMQ、MinIO 的短暂停止/恢复，其中 Redis 会话恢复、MinIO 上传失败后的 FAILED 元数据状态和核心 MySQL 计数不变均有证据；RabbitMQ 真实非法消息到 retry/DLQ/replay、MySQL 本阶段保卷重启仍未完成。阶段 10 已新增真实浏览器 WebSocket 重连和离线通知补拉证据。阶段 1 已完成生产弱 Secret 拒绝、管理面收紧、安全 Header 和 Secret 模板治理；阶段 2 已增加覆盖率门禁、两层 CI 和依赖扫描可见性；生产 TLS/WSS、外部 Secret 轮换、OWASP/NVD 完整扫描和多节点中间件高可用仍未完成。
6. 阶段 6 完成 Kind `kind-production-like` overlay、ConfigMap/Secret 分层、`taskflow.local` 本地 TLS、`/`/`/api`/`/ws` 路由定义和 namespace 内 HTTPS/WSS 适配器；既有本地证据为 HTTPS 首页/健康接口 200、WSS 升级 101，但当前集群没有 Ingress Controller，因此不计为真实生产 Ingress/TLS。阶段 10 的真实 STOMP 通知证据来自 acceptance Compose 代理链路，不升级为生产 WSS 或跨实例广播。
7. 阶段 7 按证据重新复验并形成了 80/100 的阶段性基线；其后阶段 8 以隔离 acceptance 环境完成管理员登录 smoke，阶段 9 完成 Cookie/CSRF，阶段 10 完成真实 Chromium `3 × 9/9`。默认 Maven 为 75/0/1、Testcontainers verify 为 75/0/0；同参数性能复测、真实 Ingress 和依赖风险治理仍未闭环。
8. 调优阶段 8 建立了隔离 acceptance profile、专属六服务 Compose、环境变量凭据注入、BCrypt 动态管理员哈希和 smoke 脚本；阶段 10 修复了 Principal/userId 对齐、测试编号/通知内容断言，并在不依赖人工管理员密码的环境中完成稳定浏览器验收。

阶段 0 调优冻结记录见[优化基线账本](optimization-baseline-2026-08-09.md)。阶段 1/2 未改变业务 API 契约；阶段 2 增加 JaCoCo、两层 GitHub Actions、npm audit/OWASP 扫描入口和统一门禁文档。阶段 7 实际执行 OWASP/NVD 扫描，但 hosted suppressions 连接重置且依赖告警导致质量门禁失败，未被计为“扫描通过”。

## 2. 分层验收模型

| 层级 | 检查内容 | 结果 | 说明 |
| --- | --- | --- | --- |
| L0 构建层 | Java 17、Maven、前端 TypeScript/Vite、Compose 配置 | 通过 | 后端测试、前端生产构建和 Compose 配置均通过 |
| L1 功能层 | 登录、用户上下文、任务列表、任务/项目/评论/附件/通知等代码路径 | 基本通过 | 现有单元和服务测试覆盖较完整，本轮核心 HTTP 烟测通过 |
| L2 一致性层 | Flyway、事务、状态机、乐观锁、数据范围、消息幂等、提醒恢复 | 当前范围通过 | 主要依靠代码审查和现有回归测试；完整跨基础设施 E2E 仍不足 |
| L3 安全层 | 认证撤销、限流、Token 存储、密钥、TLS、文件安全 | 部分通过 | 已有权限边界、Secret fail-fast、管理面配置、安全 Header 和附件签名校验，但 Token/TLS/外部密钥轮换生产基线尚未达标 |
| L4 运行层 | Compose 健康、重启恢复、Kubernetes 实机、性能基线、依赖扫描 | 部分通过 | Compose、Kind 应用层和本地性能基线通过；在线漏洞扫描及生产容量仍未验证 |
| L5 交付层 | README、变更记录、面试材料和限制条件是否如实 | 基本通过 | 本报告补齐高层结论，并修正认证和测试证据表述 |

### 2.1 项目级加权评分（参考 PriceSight 验收范式）

评分规则：每个维度先按 0–100 评价，再乘以权重；只对当前环境实际执行、代码/测试可复核或文档明确留档的证据计分，不把“配置目标”、Mock 结果或单机结果直接升级为生产结论。总分为各维度 `score × weight / 100` 之和，四舍五入到整数。

| 维度 | 权重 | 得分 | 加权分 | 评分依据 |
| --- | ---: | ---: | ---: | --- |
| 需求覆盖与核心功能 | 15% | 88 | 13.20 | 阶段 0–19 的认证、组织、项目/任务、评论、附件、提醒、通知、审计、前端和部署链路已具备；阶段 10 acceptance E2E 连续 3 次 9/9，真实 STOMP 通知和离线补拉通过 |
| 架构与可解释性 | 15% | 86 | 12.90 | 模块化单体边界清晰，MySQL/Redis/MQ/MinIO 职责明确，状态机、乐观锁、幂等和补偿可解释；生产拓扑仍是本地学习架构 |
| 安全与数据保护 | 15% | 78 | 11.70 | 新增并实测 HttpOnly Cookie、SameSite、prod Secure、CSRF 缺失/正确 Header、登出清 Cookie、Cookie/Redis 会话和同源 WebSocket Cookie；Bearer 兼容旁路、外部 Secret 轮换、真实 Ingress 代理隔离、生产证书链和依赖风险仍未完成；npm 有 2 个 moderate，OWASP 扫描失败并报告高危依赖 |
| 测试、构建与回归 | 15% | 90 | 13.50 | Maven 默认 75/0/1，显式 Testcontainers verify 75/0/0，JaCoCo 门禁通过，前端 typecheck/build、acceptance Compose smoke 通过；阶段 10 真实 Chromium 连续 3 次 9/9，仍未把远程 CI 运行包装为本地证据 |
| 评估可信度与可观测性 | 15% | 86 | 12.90 | acceptance 已真实验证 Cookie/CSRF/登出会话闭环、真实 STOMP MESSAGE、UI 展示和断线补拉，并保留失败 trace/截图机制；同参数性能复测仍未执行，OWASP 结果为失败而非零漏洞 |
| 运行集成与恢复就绪度 | 15% | 85 | 12.75 | Compose 六服务健康、保卷重启和真实浏览器 WebSocket 断线/重连/补拉均有证据，Kind overlay rollout 与 backend Pod 恢复通过；Ingress Controller、跨实例广播和中间件 HA 仍未验证 |
| 工程治理 | 5% | 68 | 3.40 | 已有 fast-check/integration-security 两层 CI、JaCoCo 门禁、扫描策略和 advisory/阻断规则；远程 integration-security 曾因 `./mvnw` mode 100644 返回 exit code 126，修复后尚未重新执行，actionlint 和完整分支保护仍未验证 |
| 文档与交付可信度 | 5% | 96 | 4.80 | README、CHANGELOG、阶段文档、阶段 10 浏览器报告、最终摘要、中文面试材料和本报告已同步，明确区分本地证据与生产边界 |
| **合计** | **100%** |  | **85.15 → 85** | **本地工程基线有条件通过；生产就绪不通过** |

评分解释：90–100 表示具备较强的生产候选基础，75–89 表示本地工程基线有条件通过，60–74 表示可演示但存在较大工程缺口，低于 60 表示当前验收不通过。分数不是 SLA、容量或安全认证，也不能替代目标环境的发布验收。

机器可读评分见 [`docs/project-acceptance-score-2026-08-09.json`](project-acceptance-score-2026-08-09.json)。

## 3. 本轮可复现的验证证据

### 3.1 构建与回归测试

| 命令 | 结果 |
| --- | --- |
| `.\mvnw.cmd test` | 通过：69 项执行，失败 0，跳过 1 |
| `frontend\npm run build` | 通过：TypeScript 检查和 Vite 构建通过 |
| `docker compose config --quiet` | 无 `.env` 时按必填 Secret fail-fast；使用仅存在于当前进程的非敏感测试值解析通过 |
| `kubectl kustomize k8s` | 通过 |
| `kubectl kustomize k8s\overlays\kind-production-like` | 通过：prod profile、health 管理面、TLS 路由和不覆盖 Secret 的边界可渲染 |
| `.\mvnw.cmd '-Dtaskflow.integration=true' verify` | 通过：JaCoCo HTML/XML 生成，覆盖率门禁通过；最终总行覆盖率 47.70%，核心类门槛 45% |
| `Testcontainers` 集成结果 | 上述显式 verify 共 69 项执行，失败 0，跳过 0；完成 8 条 Flyway 迁移 |
| `docker compose -f docker-compose.acceptance.yml config --quiet` | 通过：使用仅存在于当前进程的非敏感占位值解析；无变量时按设计 fail-fast |
| `scripts/acceptance-check.ps1` | 通过：health、管理员登录、`/api/auth/me`、任务列表、登出、旧会话 401 |
| `python tools/performance/performance_harness.py prepare --departments 1 --users 1 --tasks 1` | 通过：使用同一 acceptance 管理员/测试用户变量，生成 `docs/performance-acceptance-prepare-2026-08-09.json`；未输出密码或 Token |
| `npm audit --audit-level=high --registry=https://registry.npmjs.org --json` | 退出码 0：2 个 moderate，high 0，critical 0；无门槛的全量 audit 因 moderate 返回退出码 1 |
| `.\mvnw.cmd -Psecurity-scan -DskipTests verify` | 失败：约 2 分钟，hosted suppressions 连接重置且扫描报告高危依赖，不能计为扫描通过 |
| GitHub Actions YAML | 通过 PyYAML 语法解析；远程 integration-security 曾因 Maven Wrapper 权限 exit 126 失败，已修复 workflow 和 executable bit，尚待远程重跑 |

默认命令唯一跳过的是 `Stage14ContainerEnvironmentTest`。该测试默认关闭，必须显式设置 `taskflow.integration=true`；阶段 8 显式 verify 已执行 69 项、0 失败、0 跳过，并完成全新 MySQL 的 8 条 Flyway 迁移。

阶段 4 前端构建通过，并对 Dashboard、Login、Management、Task 使用路由级懒加载。旧入口 JS 为 1,156,965 bytes，新构建最大共享 chunk 为 747,991 bytes，入口相关 chunk 下降约 35.38%；但最大 chunk 仍超过 Vite 默认 500 KB 提示阈值，且没有首屏网络抓包，因此不把它描述为总传输量或用户体验已改善。完整对比见 `docs/performance-comparison-2026-08.md`。

阶段 15 原有本地性能基线已完成：10 个部门、100 个用户、1000 个任务，20 并发，预热 10 秒，采样 60 秒；登录、任务列表、任务详情、任务创建、状态更新和通知列表六个场景均为 0 错误。阶段 8 已用 acceptance 管理员完成少量性能数据准备，证明脚本不再依赖人工数据库密码；完整 10/100/1000 同参数优化后复测仍未执行，因此没有生成新的 QPS/p95/p99 结论。详见 `docs/performance-baseline-after-optimization.json`、`docs/performance-runtime-after-optimization.json`、`docs/performance-explain-after-optimization.txt` 和 `docs/performance-comparison-2026-08.md`。

阶段 2 的依赖与质量门禁详细记录见[依赖安全与质量门禁记录](dependency-security-report.md)。

阶段 6 的 Kind 生产样式本地验证见[Kind 生产样式本地验证记录](kind-production-like-validation.md)。本阶段 HTTPS 首页和 `/api/health` 返回 200，WSS Upgrade 返回 101；由于集群没有 `IngressClass/nginx` 对应的控制器，实际 Ingress 资源没有运行态 ADDRESS，namespace 内 Nginx edge 只作为本地验证适配器。该结果不覆盖云 LB、真实证书轮换、生产 Secret Manager、跨节点 WebSocket 会话或多节点 HA。

### 3.6 故障注入与恢复

阶段 5 的完整矩阵见[故障注入与恢复验收记录](fault-injection-2026-08-09.md)。真实演练结果如下：

- Redis 停止期间认证会话检查不可用，恢复后 HTTP 200；Redis ZSet 恢复，Flyway V8、任务 3628、通知 2000、附件 0 前后不变。
- RabbitMQ 停止期间后端健康接口仍 HTTP 200，恢复后 dispatch/retry/dead 队列重新可查询且 ready/unacked 为 0；真实非法消息注入被执行审批拒绝，不能计为 retry/DLQ/replay 通过。
- MinIO 停止期间附件上传 HTTP 500，任务 2 附件状态为 FAILED=1；MinIO 恢复后 live health HTTP 200。对象列表未直接采集，未声称无孤儿对象。
- MySQL 停止和 backend 重启本阶段被执行审批拒绝；既有保卷重启记录继续有效，但不作为本阶段新增证据。阶段 10 已新增真实浏览器 WebSocket 重连和离线补拉证据；这不改变单实例和本地环境边界。

上述结果证明了本地单实例短故障下的部分恢复边界，不证明生产 HA、故障切换、数据服务 SLA 或跨节点 WebSocket 会话迁移。

### 3.5 浏览器 E2E

阶段 3 新增 Playwright 9 个浏览器场景。阶段 10 使用隔离 acceptance Compose 和统一环境变量真实执行，连续三次结果均为 **9 通过、0 失败**：登录、401、403、任务真实写入/更新、重复提交、登出失效、附件、真实 STOMP 通知和断线补拉全部通过。真实 WebSocket 场景要求收到包含本次任务编号的 `MESSAGE`，并在通知中心显示该编号；完整矩阵见 [`docs/e2e-browser-report-2026-08-10.md`](e2e-browser-report-2026-08-10.md)。

本阶段已修正登录完成等待、Modal 提交定位、测试编号规范化、通知内容断言，并完成 STOMP Principal/userId 对齐和后端回归测试。阶段 10 真实浏览器证据已闭环；失败时的截图、视频和 trace 机制仍保留在 `frontend/test-results/`，最终结果和边界见 [`docs/e2e-browser-report-2026-08-10.md`](e2e-browser-report-2026-08-10.md)。

### 3.2 Compose 运行态

本轮未用假值重建现有 Compose 应用容器，也没有删除卷或重建数据库；当前已运行的六服务 Compose 保持可用。六个服务均为 `healthy`：

- backend
- frontend
- mysql
- redis
- rabbitmq
- minio

核心运行态结果：

| 检查 | 结果 |
| --- | --- |
| `GET http://localhost:8080/api/health` | HTTP 200 |
| `GET http://localhost:5173/` | HTTP 200 |
| 管理员登录 | 通过，未在报告中记录密码或 Token |
| `GET /api/auth/me` | 通过，返回当前 `admin` 用户 |
| 登出后使用旧 Token 访问 `/api/auth/me` | HTTP 401 |
| `GET /api/tasks?page=1&size=20` | 通过 |
| 缺少 Authorization 访问受保护接口 | HTTP 401 |
| 非法 JWT 访问受保护接口 | HTTP 401 |
| Redis `PING` | `PONG` |
| RabbitMQ diagnostics ping | 通过 |
| MinIO live health | HTTP 200 |
| Flyway 最新迁移 | V8 |

此前已执行过保留数据卷的 Compose 重启验收；本阶段未重复重启，以避免在缺少真实 `.env` 时触发不必要的容器重建。当前认证烟测仅记录状态码，不记录密码、JWT 或完整响应。

### 3.3 Kubernetes 证据边界

Kustomize 静态渲染和 Kind 实机验证均通过。当前集群为 `dev`、context `kind-dev`、Kubernetes `v1.32.2`、节点 `dev-control-plane` 为 `Ready`；客户端 PATH 中的 `kubectl` 为 v1.36.1，`kind` 为 v0.27.0。

- `backend` Deployment 为 2/2，`frontend` Deployment 为 1/1；
- 前后端 startup/readiness/liveness 探针均支持 rollout 完成；
- 前端端口转发首页和后端 `/api/health` 均返回 HTTP 200；
- 删除后端 Pod 后 Deployment 自动创建替代 Pod；
- 前后端滚动重启均成功，Service 通过就绪端点提供访问。

阶段 6 在上述应用层基础上新增 `kind-production-like` overlay：ConfigMap/Secret 分层、prod profile、`taskflow.local` 本地自签 TLS、静态 Ingress `/`/`/api`/`/ws` 路由和 namespace 内 HTTPS/WSS edge。HTTPS 首页与 `/api/health` 返回 200，WSS Upgrade 返回 101。当前集群没有 Ingress Controller，Ingress 资源没有运行态 ADDRESS，因此该 edge 只作为本地传输验证适配器。

边界：本次只覆盖 Kind 单节点应用层和本地 edge，不覆盖生产多节点高可用、中间件 HA、真实 Ingress Controller、云 LB、证书轮换、HPA 或故障域隔离。

### 3.4 调优基线冻结

阶段 0 已完成当前目录、Git、工具缓存路径、端口、Flyway V1–V8、测试入口、Compose 入口和 Kubernetes 入口核实，并建立阶段 1～7 的目标/证据/完成条件/评分影响矩阵。阶段 1 在此基础上补充了 66/0/1 后端回归、生产弱 Secret 拒绝测试、安全 Header 测试、前端构建、Compose 模板解析、Kustomize 渲染和旧 Token 失效烟测。

## 4. 阶段覆盖评估

| 阶段 | 高层结论 | 验收状态 |
| --- | --- | --- |
| 0 | 工程初始化、Java 17、Compose 基础设施和健康检查具备 | 通过当前范围 |
| 1 | Flyway V1 基础模型和文档索引具备 | 通过当前范围 |
| 2 | 参数校验、统一错误、Trace ID 和异常边界具备 | 通过当前范围 |
| 3 | BCrypt、JWT、Redis 活动会话、后端登出、Token 主动失效和登录限流已实现；Compose 旧 Token 和限流烟测通过 | **通过当前范围** |
| 4 | 用户、角色、部门、权限管理和方法级授权具备 | 通过当前范围 |
| 5 | 数据范围和服务层越权校验具备 | 通过当前范围 |
| 6 | 任务查询、分页、负责人和操作日志具备 | 通过当前范围 |
| 7 | 状态机和旧状态 + version 条件更新具备 | 通过当前范围 |
| 8 | 评论、附件元数据、MinIO 对象和失败补偿具备 | 通过当前范围，未包含病毒扫描 |
| 9 | 持久提醒计划、Redis 索引、锁和恢复路径具备 | Redis 短故障恢复实测；真实到期提醒重建未在本阶段等待验证 |
| 10 | RabbitMQ 手动 Ack、有限重试、死信和通知幂等具备 | RabbitMQ 容器恢复实测；真实消息 retry/DLQ/replay 未完成 |
| 11 | STOMP 用户目的地、断线清理和通知补拉具备 | Cookie 握手、Principal/userId 对齐、真实 Chromium MESSAGE、通知 UI、断线重连和 HTTP 补拉均通过；Bearer 仅为兼容客户端 |
| 12 | 审计、Trace、敏感值排除和健康探针具备 | 通过当前范围 |
| 13 | React 核心页面、401 处理、重复提交保护和通知中心具备 | acceptance Chromium E2E 连续 3 次 9/9，包含真实通知 UI 和断线补拉 |
| 14 | 回归测试体系、Testcontainers 和 Playwright 入口具备 | 后端回归、Testcontainers、前端构建和真实 Chromium E2E 均有通过证据；远程 CI 仍需单独复验 |
| 15 | 性能数据准备、六个 HTTP 场景、EXPLAIN 和运行时观测 | 原固定参数基线通过；阶段 8 acceptance 数据准备通过，同参数优化后完整复测未执行，生产容量未验证 |
| 16 | 六服务 Compose、健康检查、持久卷和重启恢复具备 | Redis/RabbitMQ/MinIO 短恢复实测；MySQL 本阶段未复验，既有保卷重启证据保留 |
| 17 | 应用层 Kubernetes 清单、探针、双副本和滚动策略 | Kind `dev` 实机部署、健康、恢复和滚动重启通过 |
| 6（调优阶段） | Kind 生产样式 Ingress/TLS、可信代理、配置分层 | Kustomize、overlay、rollout、HTTPS 和 WSS 握手通过；无 Ingress Controller，STOMP/浏览器通知闭环未通过，不计生产验收 |
| 18 | 已完成安全质量审查和若干硬化修复 | 部分通过，生产风险仍存在 |
| 19 | 中文面试/简历材料和事实边界具备 | 通过文档审查 |

## 5. 架构与工程质量判断

### 做得较好的部分

- 采用模块化单体，业务边界比无必要拆分微服务更适合当前规模和学习目标。
- MySQL 保存任务、权限、通知、提醒计划、审计和附件元数据等事实；Redis 主要用于提醒索引和分布式锁，没有被当作核心业务唯一数据源。
- 任务状态变化使用固定状态机和数据库条件更新，能够处理旧状态或版本冲突，不依赖 JVM 内存锁。
- 关键写操作有事务边界，提醒采用提交后事件，避免在核心事务中进行不必要的网络调用。
- RabbitMQ 消费具有消息幂等、手动 Ack、有限重试和死信补偿，避免无限重试拖垮队列。
- 权限不仅隐藏前端按钮，Controller 和 Service 均存在后端授权/数据范围检查。
- 附件对象与 MySQL 元数据分离，并增加文件签名校验、大小限制、对象键隔离和失败补偿。
- 阶段 18 已修复信任客户端 `X-Forwarded-For` 和仅凭 MIME 判断附件内容的问题，安全审查没有把这些风险继续隐藏在“已完成”描述里。
- 阶段 1 已将 dev/test/prod 配置边界、生产弱 Secret fail-fast、Spring Security/Nginx 基础 Header、prod Actuator/OpenAPI 暴露范围和 Secret 模板治理纳入可复核证据；阶段 9 已实测浏览器 HttpOnly Cookie、CSRF、prod Secure 和 Cookie WebSocket 握手，Bearer 仅保留兼容模式。

### 仍需正视的工程边界

- 当前认证采用无状态 JWT 请求模型，并通过 Redis 活动会话标记补充主动登出和会话失效；这是对阶段 3 原始 Redis 会话要求的兼容实现，真实 Redis 容器行为仍需补验。
- 浏览器 Token 已移出 `localStorage`；登录接口已增加 Redis 失败限流，但仍没有账号锁定、验证码升级、外部 Secret Manager/轮换和生产代理/TLS闭环。
- `.env.example` 和 Kubernetes Secret 模板已改为空值安全模板，Compose 敏感变量改为必填；Kind overlay 不覆盖外部注入的 Secret；但外部 Secret Manager、轮换、审计和实际生产注入仍未验证。
- prod 已关闭 Swagger/OpenAPI、仅开放 Actuator health 配置，前端 Nginx 只代理精确健康路径；真实生产网络隔离和管理面专用入口仍未验证。
- Kind 本地 edge 已验证 HTTPS 首页、`/api/health` 和 WSS 101 握手；生产部署仍必须使用受控 Ingress/反向代理、真实证书链、可信代理边界和安全 Header 策略。STOMP 通知闭环尚未通过。
- MySQL、Redis、RabbitMQ、MinIO 在本地拓扑中均为单实例，Kubernetes 只托管前后端应用层，不构成生产高可用。
- npm 官方 audit 已执行并发现 2 个 moderate；OWASP Dependency-Check 已执行但因 hosted suppressions 连接重置和高危依赖告警失败，不能描述为零漏洞；阶段 15 已有本机性能基线，阶段 8 仅完成 acceptance 小规模数据准备，最终同参数复测尚未完成。

## 6. 高优先级问题清单

以下 P0 仅表示“生产发布门禁”；它们不否定本地学习和演示范围已经通过的部分。

| 优先级 | 问题 | 影响 | 建议验收条件 |
| --- | --- | --- | --- |
| P0 | 外部 Secret 注入、轮换和审计尚未完成 | 生产凭据生命周期仍依赖部署平台正确配置 | 接入 Secret Manager/Sealed Secrets，完成启动拒绝默认值和密钥轮换演练 |
| P0 | 生产 TLS/WSS、可信代理、证书轮换和外部 Secret 生命周期未完成 | Cookie 已降低浏览器 Token 读取面，但窃听、代理误配或密钥生命周期问题仍可能放大风险 | 完成真实 Ingress/TLS/WSS、代理 Header 清洗、Secret Manager/轮换和目标环境发布验收 |
| P0 | MySQL、Redis、RabbitMQ、MinIO 仍为单实例 | 无法满足生产故障域和数据服务高可用 | 明确目标 HA 拓扑，完成备份/恢复、故障切换和消息积压演练 |
| P1 | 只有窗口限流，尚无账号锁定/验证码升级 | 公开部署仍需更强的密码猜测防护 | 保留按账号和来源限流，同时补充锁定、验证码升级、审计和生产策略 |
| P1 | OWASP 扫描失败且输出高危依赖告警；npm 有 2 个 moderate | 当前依赖风险不能作为生产门禁通过 | 按报告逐项核实、升级或经审查抑制，再运行完整 Maven/npm 扫描并保存结果 |
| P2 | 前端最大共享 chunk 仍偏大、优化后压测未复测、Rabbit/数据库故障闭环证据不完整 | 首屏、性能变化、Rabbit retry/DLQ/replay 和数据库事实快照仍未闭环量化 | 完成同参数性能对比、Rabbit retry/DLQ/replay、数据库事实快照和更完整故障恢复证据 |

## 7. 交付判定

| 交付目标 | 判定 | 依据 |
| --- | --- | --- |
| 本地学习与功能演示 | **通过当前范围** | Compose 六服务健康与保卷重启通过，health/401/非法凭据烟测通过，Kind 应用层 rollout/Pod 恢复通过；阶段 6 既有本地 HTTPS/WSS 传输证据保留 |
| 面试项目与中文材料 | **通过当前范围** | 阶段 19 材料已按本报告证据更新，明确个人贡献和未验证边界 |
| 本地工程基线 | **有条件通过，85/100** | Cookie/CSRF、无 localStorage 浏览器登录、真实 Chromium 3×9/9、STOMP MESSAGE、断线补拉新增证据；同参数性能、依赖风险和生产 HA 仍未闭环 |
| 生产发布 | **不通过** | 外部 Secret/TLS 轮换、Token、真实 Ingress/代理、单实例中间件、依赖告警治理、目标环境容量和完整 E2E 尚未满足门禁 |

### 可以对外描述的事实

- 这是一个可在本地 Compose 环境启动的 Java 17 + React 模块化单体任务协同平台。
- 已实现任务、项目、权限、数据范围、附件、提醒、RabbitMQ 通知、WebSocket 通知中心、审计和本地部署等完整学习链路。
- 阶段 9 后默认回归为 75 项执行、0 失败、1 项可选 Testcontainers 测试跳过；显式 integration verify 为 75/0/0，阶段9 Cookie/CSRF关键测试 10/0/0，前端 typecheck/build、acceptance 六服务健康、Cookie smoke 均有新证据。阶段 10 真实 Chromium 连续三次执行 9/9，包含真实 STOMP MESSAGE、通知 UI、断线重连和 HTTP 补拉；同参数性能复测仍未完成，不能描述为生产容量或生产 HA 已验证。
- 任务状态机、乐观锁、消息幂等、有限重试、死信补偿和 MinIO 元数据分离是项目中可以重点讲解的工程设计。

### 当前不能对外描述的事实

- 可以说当前本地 Compose 已验证认证登出和 Token 撤销；不能将其描述为生产级认证安全基线。
- 可以说 Kind 单节点已完成应用层 Pod 恢复和滚动更新验证；不能把它描述为生产级 Kubernetes 高可用。
- 可以引用 `docs/performance-baseline.json` 中的本机实测结果，但不能将其外推为生产 QPS、延迟或容量。
- 不能把本地 Compose 单实例中间件描述为生产级高可用。
- 可以说官方 npm audit 当前 high/critical 为 0 且有 2 个 moderate；不能说 Maven/npm/OWASP 依赖零漏洞，OWASP 本轮扫描因告警失败。
- 可以说阶段 6 既有本地 TLS/WSS Upgrade 101 证据；不能把 WSS 握手描述为完整 STOMP 鉴权、订阅、通知可靠性或生产 Ingress 验收。

## 8. 下一步建议

建议按以下顺序推进，而不是继续增加外围功能：

1. 优先关闭 P0：Secret 管理、Token 存储策略、真实 TLS/WSS/Ingress 和中间件高可用。
2. 优先治理 OWASP 高危告警与 React Router moderate advisory，再运行完整依赖扫描和回归。
3. 修复 3 条 acceptance 浏览器失败，完成 9/9 E2E、同参数性能复测及 Rabbit/DLQ/数据库事实快照。

本报告不替代各阶段技术文档；它是当前项目是否适合演示、面试或继续生产化推进的总判断入口。
