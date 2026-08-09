# TaskFlow Platform 项目全面验收与高维度评估报告（2026-08-09）

## 1. 验收结论

本报告是对阶段 0 至阶段 19 的一次独立复核，重点检查的不只是“代码是否存在”，还包括：需求覆盖、架构边界、数据一致性、外部依赖、运行恢复、安全风险、测试证据、部署证据和文档可信度。本版增加参考 PriceSight 项目采用的项目级加权评分，但评分不替代生产发布门禁。

最终结论：

> **阶段 3 后项目级评分建议：81/100。结论：本地工程基线有条件通过，可用于学习、演示和面试；生产交付仍不通过。**

主要原因不是基础功能无法运行；本轮已完成认证、Compose、Kind 应用层和性能基线的本地证据，但仍有以下重要边界：

1. Redis 会话、后端登出、HTTP/WebSocket 会话撤销和登录失败限流已实现；真实 Compose“登录-登出-旧 Token 失效”和限流烟测均通过。
2. 阶段 14 的 Testcontainers 专用测试已通过；Testcontainers 1.21.4 成功启动 MySQL、Redis、RabbitMQ、MinIO，并完成全新 MySQL 的 8 条 Flyway 迁移。
3. 已在 Kind 本地集群 `dev`/`kind-dev` 完成前后端 Deployment 调度、探针、滚动更新、Pod 故障恢复和 Service 端口转发健康检查验收。
4. 阶段 15 已生成固定参数下的性能、运行时指标和 EXPLAIN 报告；这些数字只代表当前 Windows 11 本机，不代表生产容量。
5. 阶段 1 已完成生产弱 Secret 拒绝、管理面收紧、安全 Header 和 Secret 模板治理；阶段 2 已增加覆盖率门禁、两层 CI 和依赖扫描可见性；阶段 3 已新增并部分执行浏览器 E2E，但 9 个场景尚未全通过，真实 STOMP 订阅仍待修复后复验。生产级 Token 存储、TLS/WSS、外部 Secret 轮换、OWASP/NVD 完整扫描和多节点中间件高可用仍未完成。

阶段 0 调优冻结记录见[优化基线账本](optimization-baseline-2026-08-09.md)。阶段 1/2 未改变业务 API 契约；阶段 2 增加 JaCoCo、两层 GitHub Actions、npm audit/OWASP 扫描入口和统一门禁文档。OWASP/NVD 本地扫描超过 5 分钟未生成报告，未被计为“扫描通过”。

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
| 需求覆盖与核心功能 | 15% | 86 | 12.90 | 阶段 0–19 的认证、组织、项目/任务、评论、附件、提醒、通知、审计、前端和部署链路已具备；完整浏览器 E2E、病毒扫描等仍缺失 |
| 架构与可解释性 | 15% | 86 | 12.90 | 模块化单体边界清晰，MySQL/Redis/MQ/MinIO 职责明确，状态机、乐观锁、幂等和补偿可解释；生产拓扑仍是本地学习架构 |
| 安全与数据保护 | 15% | 73 | 10.95 | 已验证后端鉴权、会话撤销、登录窗口限流、权限/数据范围、附件签名校验、prod 弱 Secret 拒绝、安全 Header 和管理面收紧；npm audit 已形成可见性，Token localStorage、明文本地链路、外部 Secret 轮换、OWASP/NVD 未完成和生产代理隔离仍扣分 |
| 测试、构建与回归 | 15% | 88 | 13.20 | Maven 67/0/1、Testcontainers、JaCoCo 报告/门禁、前端 typecheck/build、Compose 模板解析和 Kind 清单/运行验证均有证据；浏览器 E2E 已部分执行但为 4/9，远程 CI 仍未实跑 |
| 评估可信度与可观测性 | 15% | 80 | 12.00 | 已有性能基线、运行时指标、EXPLAIN、Actuator、JaCoCo 和依赖扫描结果文件；npm audit 已实际完成，OWASP/NVD 超时，数据仍主要来自单机 |
| 运行集成与恢复就绪度 | 15% | 76 | 11.40 | 六服务 Compose 健康、保卷重启、Kind 前后端探针/Pod 恢复/滚动重启通过；中间件仍是单实例，无 Ingress/TLS/HPA/多节点 HA |
| 工程治理 | 5% | 68 | 3.40 | 已有 fast-check/integration-security 两层 CI、JaCoCo 门禁、扫描策略和 advisory/阻断规则；远程 workflow 尚未执行，actionlint 和完整分支保护仍未验证 |
| 文档与交付可信度 | 5% | 92 | 4.60 | README、CHANGELOG、阶段文档、中文面试材料和本报告已同步，明确区分本地证据与生产边界 |
| **合计** | **100%** |  | **81.35 → 81** | **本地工程基线有条件通过；生产就绪不通过** |

评分解释：90–100 表示具备较强的生产候选基础，75–89 表示本地工程基线有条件通过，60–74 表示可演示但存在较大工程缺口，低于 60 表示当前验收不通过。分数不是 SLA、容量或安全认证，也不能替代目标环境的发布验收。

机器可读评分见 [`docs/project-acceptance-score-2026-08-09.json`](project-acceptance-score-2026-08-09.json)。

## 3. 本轮可复现的验证证据

### 3.1 构建与回归测试

| 命令 | 结果 |
| --- | --- |
| `.\mvnw.cmd test` | 通过：67 项执行，失败 0，跳过 1 |
| `frontend\npm run build` | 通过：TypeScript 检查和 Vite 构建通过 |
| `docker compose config --quiet` | 无 `.env` 时按必填 Secret fail-fast；使用仅存在于当前进程的非敏感测试值解析通过 |
| `kubectl kustomize k8s` | 通过 |
| `.\mvnw.cmd verify` | 通过：JaCoCo HTML/XML 生成，覆盖率门禁通过；总行覆盖率 47.20%，核心类门槛 45% |
| `.\mvnw.cmd "-Dtaskflow.integration=true" verify` | 通过：66 项单测/回归 + 1 项 Testcontainers 集成测试，0 失败；完成 8 条 Flyway 迁移 |
| `npm audit`（官方 registry） | 通过：2 个 moderate，high 0，critical 0；镜像 registry 的 audit API 另有 404，未混淆记录 |
| `.\mvnw.cmd -Psecurity-scan -DskipTests verify` | 未完成：超过 5 分钟超时，未生成 OWASP/NVD 报告，不计为漏洞为 0 |
| GitHub Actions YAML | 通过 PyYAML 语法解析；本机未安装 actionlint，远程 workflow 尚未执行 |

默认命令唯一跳过的是 `Stage14ContainerEnvironmentTest`。该测试默认关闭，必须显式设置 `taskflow.integration=true`；显式执行已通过 1 项测试，并完成全新 MySQL 的 8 条 Flyway 迁移。

前端构建有一个既有质量警告：压缩后的主 JS chunk 约 1.16 MB，超过 Vite 默认 500 KB 提示阈值。它不阻断构建，但应在后续通过路由级懒加载或手工分包改善首屏传输。

阶段 15 本地性能基线已完成：10 个部门、100 个用户、1000 个任务，20 并发，预热 10 秒，采样 60 秒；登录、任务列表、任务详情、任务创建、状态更新和通知列表六个场景均为 0 错误。完整结果见 `docs/performance-baseline.json`，运行时指标见 `docs/performance-runtime.json`，EXPLAIN 输出见 `docs/performance-explain.txt`。这些是当前 Windows 11 本机数据，不代表生产容量或稳定用户数。

阶段 2 的依赖与质量门禁详细记录见[依赖安全与质量门禁记录](dependency-security-report.md)。

### 3.5 浏览器 E2E

阶段 3 新增 Playwright 9 个浏览器场景，最后一次现有 Compose 后端 + 源码前端 + Chromium 的可复核结果为 **4 通过、5 失败**。登录、401、403、登出失效通过；任务写入/更新、重复提交、附件和通知场景受到前置定位/运行环境问题影响。浏览器真实创建了 `/ws/notifications` 并收到 STOMP `CONNECTED`，但 SUBSCRIBE 后收到服务端 `ERROR` 并以 1002 关闭，因此不能计为真实 WebSocket 通知通过。

本阶段已修正登录完成等待、Modal 提交定位、测试标题隔离，并为后续 STOMP 帧补充会话 Principal 传播和后端回归测试。修复后的完整浏览器复验因临时本地后端无法使用现有数据库管理员密码而未完成；不得将代码修复或单元测试升级为浏览器 E2E 绿灯。失败截图、视频和 trace 保存在 `frontend/test-results/`，具体矩阵见 [`docs/e2e-browser-report-2026-08-09.md`](e2e-browser-report-2026-08-09.md)。

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

边界：本次只覆盖 Kind 单节点应用层，不覆盖生产多节点高可用、中间件 HA、Ingress/TLS、HPA 或故障域隔离。

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
| 9 | 持久提醒计划、Redis 索引、锁和恢复路径具备 | 通过代码/测试当前范围 |
| 10 | RabbitMQ 手动 Ack、有限重试、死信和通知幂等具备 | 通过代码/测试当前范围 |
| 11 | STOMP JWT、用户目的地、断线清理和通知补拉具备 | 代码/单元测试通过；浏览器已真实握手但订阅失败，未完整验收 |
| 12 | 审计、Trace、敏感值排除和健康探针具备 | 通过当前范围 |
| 13 | React 核心页面、401 处理、重复提交保护和通知中心具备 | 基本通过；浏览器 E2E 4/9，完整交互仍未通过 |
| 14 | 回归测试体系、Testcontainers 和 Playwright 入口具备 | 代码/后端回归通过；浏览器 E2E 仍未全通过 |
| 15 | 性能数据准备、六个 HTTP 场景、EXPLAIN 和运行时观测 | 本地固定参数基线通过，生产容量未验证 |
| 16 | 六服务 Compose、健康检查、持久卷和重启恢复具备 | 通过本轮本地验收 |
| 17 | 应用层 Kubernetes 清单、探针、双副本和滚动策略 | Kind `dev` 实机部署、健康、恢复和滚动重启通过 |
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
- 阶段 1 已将 dev/test/prod 配置边界、生产弱 Secret fail-fast、Spring Security/Nginx 基础 Header、prod Actuator/OpenAPI 暴露范围和 Secret 模板治理纳入可复核证据；前端 Bearer Token 仍在 localStorage，未把未完成的 Cookie 迁移计为已解决。

### 仍需正视的工程边界

- 当前认证采用无状态 JWT 请求模型，并通过 Redis 活动会话标记补充主动登出和会话失效；这是对阶段 3 原始 Redis 会话要求的兼容实现，真实 Redis 容器行为仍需补验。
- 前端 Token 放在 `localStorage`；登录接口已增加 Redis 失败限流，但仍没有账号锁定、验证码升级和生产级 Token 存储策略。
- `.env.example` 和 Kubernetes Secret 模板已改为空值安全模板，Compose 敏感变量改为必填；但外部 Secret Manager、轮换、审计和实际生产注入仍未验证。
- prod 已关闭 Swagger/OpenAPI、仅开放 Actuator health 配置，前端 Nginx 只代理精确健康路径；真实生产网络隔离和管理面专用入口仍未验证。
- 当前 HTTP/WebSocket 为本地明文连接；生产部署必须补齐 TLS/WSS、可信代理和安全 Header 策略。
- MySQL、Redis、RabbitMQ、MinIO 在本地拓扑中均为单实例，Kubernetes 只托管前后端应用层，不构成生产高可用。
- 尚未完成在线漏洞数据库驱动的 Maven/npm/OWASP 依赖扫描；阶段 15 已有本机性能基线，但不能外推生产容量。

## 6. 高优先级问题清单

以下 P0 仅表示“生产发布门禁”；它们不否定本地学习和演示范围已经通过的部分。

| 优先级 | 问题 | 影响 | 建议验收条件 |
| --- | --- | --- | --- |
| P0 | 外部 Secret 注入、轮换和审计尚未完成 | 生产凭据生命周期仍依赖部署平台正确配置 | 接入 Secret Manager/Sealed Secrets，完成启动拒绝默认值和密钥轮换演练 |
| P0 | Token localStorage、HTTP/WSS 明文和生产边界未完成 | XSS、窃听或 Actuator/OpenAPI 暴露会放大风险 | 采用 HttpOnly Cookie + CSRF，或完成 CSP/XSS 基线；补齐 TLS/WSS、可信代理、安全 Header 和管理面隔离 |
| P0 | MySQL、Redis、RabbitMQ、MinIO 仍为单实例 | 无法满足生产故障域和数据服务高可用 | 明确目标 HA 拓扑，完成备份/恢复、故障切换和消息积压演练 |
| P1 | 只有窗口限流，尚无账号锁定/验证码升级 | 公开部署仍需更强的密码猜测防护 | 保留按账号和来源限流，同时补充锁定、验证码升级、审计和生产策略 |
| P1 | 在线依赖扫描未完成 | 无法给出漏洞数据库驱动的依赖风险结论 | 补充 npm/Maven/OWASP 扫描并保存报告；性能基线已保存但仍需在目标环境复测 |
| P2 | 前端主包偏大、浏览器 E2E 尚未全通过 | 首屏和真实交互风险仍未闭环量化 | 保留失败证据，完成 9/9 场景并验证真实 STOMP `MESSAGE`；再评估路由懒加载/分包 |

## 7. 交付判定

| 交付目标 | 判定 | 依据 |
| --- | --- | --- |
| 本地学习与功能演示 | **通过当前范围** | Compose 六服务健康，核心 API 烟测通过，Kind 应用层运行与恢复通过 |
| 面试项目与中文材料 | **通过当前范围** | 阶段 19 材料已按本报告证据更新，明确个人贡献和未验证边界 |
| 本地工程基线 | **有条件通过，81/100** | 主要测试、构建、部署和性能证据已留档；浏览器 E2E 已建立但未全通过，安全仍有 P0/P1 项 |
| 生产发布 | **不通过** | Secret/TLS/Token、单实例中间件、在线扫描、目标环境容量和完整 E2E 尚未满足门禁 |

### 可以对外描述的事实

- 这是一个可在本地 Compose 环境启动的 Java 17 + React 模块化单体任务协同平台。
- 已实现任务、项目、权限、数据范围、附件、提醒、RabbitMQ 通知、WebSocket 通知中心、审计和本地部署等完整学习链路。
- 当前默认回归测试为 67 项执行、0 失败、1 项可选 Testcontainers 测试跳过；显式阶段 14 Testcontainers 测试另行通过 1 项。Compose 六服务健康、Kind 应用层实机验证和阶段 15 本地性能基线均有留档。浏览器 E2E 当前为部分执行结果，不能描述为全链路通过。
- 任务状态机、乐观锁、消息幂等、有限重试、死信补偿和 MinIO 元数据分离是项目中可以重点讲解的工程设计。

### 当前不能对外描述的事实

- 可以说当前本地 Compose 已验证认证登出和 Token 撤销；不能将其描述为生产级认证安全基线。
- 可以说 Kind 单节点已完成应用层 Pod 恢复和滚动更新验证；不能把它描述为生产级 Kubernetes 高可用。
- 可以引用 `docs/performance-baseline.json` 中的本机实测结果，但不能将其外推为生产 QPS、延迟或容量。
- 不能把本地 Compose 单实例中间件描述为生产级高可用。

## 8. 下一步建议

建议按以下顺序推进，而不是继续增加外围功能：

1. 优先关闭 P0：Secret 管理、Token 存储策略、TLS/WSS、Actuator/OpenAPI 访问边界和中间件高可用。
2. 补充在线 Maven/npm/OWASP 依赖扫描、覆盖率/CI 质量门禁和完整浏览器 E2E。
3. 在目标部署环境按相同数据规模和场景复测阶段 15，结合数据库索引、连接池和消息堆积指标形成容量结论。

本报告不替代各阶段技术文档；它是当前项目是否适合演示、面试或继续生产化推进的总判断入口。
