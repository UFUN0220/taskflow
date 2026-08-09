# TaskFlow Platform 调优基线（2026-08-09）

## 1. 基线目的与冻结结论

本文件冻结 TaskFlow Platform 下一轮调优前的可复核事实，作为阶段 1～7 的比较基准。本阶段不修改业务代码，不调整数据库结构，不删除 Docker 卷或业务数据，也不因为建立基线而调整项目评分。

当前项目级评分保持 **79/100**：

- 本地学习、演示和面试：有条件通过；
- 本地工程基线：有条件通过；
- 生产发布：不通过。

评分方法、权重和生产门禁见[全面验收报告](project-acceptance-report-2026-08-09.md)及[结构化评分结果](project-acceptance-score-2026-08-09.json)。

## 2. 仓库与环境基线

| 项目 | 当前事实 |
| --- | --- |
| 项目目录 | `F:\projects_2027\taskflow-platform\backend` |
| Git 分支 | `master` |
| Git HEAD | `b67d19c8bb6a8c2dfed508453786df5ded74e7c8` |
| 工作区 | DIRTY；包含此前验收、性能报告、Kind 脚本和文档修改，未覆盖或清理 |
| Java | `F:\JDK17`，Java 17 |
| Maven 用户目录 | `F:\newinstall\maven-user` |
| Maven 本地仓库 | `F:\newinstall\maven-repository` |
| Gradle 用户目录 | `F:\newinstall\gradle-user-home` |
| npm 缓存 | `F:\newinstall\npm-cache` |
| Docker | Docker Desktop；`DOCKER_HOST` 已设置 |
| Testcontainers | `TESTCONTAINERS_DOCKERCONFIG_SOURCE=autoIgnoringUserProperties` |
| Kubernetes context | `kind-dev` |
| Kind 集群 | `dev`，Kubernetes `v1.32.2`，单节点 `dev-control-plane` Ready |

### 当前工作区变更

本阶段没有新增业务代码变更。阶段0文档新增后，工作区仍包含此前已有的验收和性能产物，主要包括：

- `README.md`、`CHANGELOG.md`；
- `docs/` 下验收、性能、Kubernetes 和阶段19文档；
- `scripts/deploy-k8s.ps1`；
- `docs/performance-*.json`、`docs/performance-explain.txt`；
- `docs/project-acceptance-score-2026-08-09.json`；
- 本文件 `docs/optimization-baseline-2026-08-09.md`。

## 3. 项目结构与入口

### 主要目录

```text
backend/
├─ src/main/java/yvon/backend/       # Spring Boot 模块化单体
├─ src/test/java/yvon/backend/       # Java 回归测试
├─ src/main/resources/db/migration/  # Flyway V1-V8
├─ frontend/                         # React + TypeScript + Vite
├─ docker-compose.yml                # 六服务本地容器拓扑
├─ k8s/                              # 前后端应用层 Kubernetes 清单
├─ scripts/                          # Compose/Kubernetes PowerShell 入口
├─ tools/performance/                # 阶段15数据、压测、观测和EXPLAIN工具
└─ docs/                             # 阶段文档与验收证据
```

### 启动和验证入口

| 范围 | 入口 | 当前用途 |
| --- | --- | --- |
| 后端回归 | `\.\mvnw.cmd test` | 默认 Docker 独立 Maven 测试 |
| 前端构建 | `Set-Location frontend; npm run build` | TypeScript 检查和 Vite 生产构建 |
| Compose 初始化 | `\.\scripts\init-compose.ps1 -Rebuild` | 构建并启动六服务；本阶段未执行 |
| Compose 停止 | `\.\scripts\cleanup-compose.ps1` | 停止但保留卷；本阶段未执行 |
| Compose 配置 | `docker compose config --quiet` | 静态验证；本阶段审批通道未返回结果 |
| Kubernetes 渲染 | `kubectl kustomize k8s` | 静态渲染；本阶段通过 |
| Kubernetes 部署 | `\.\scripts\deploy-k8s.ps1 -Runtime kind -KindClusterName dev` | Kind 本地镜像加载和清单部署；本阶段未重新部署 |

### 端口与依赖

| 服务 | 容器/应用端口 | 宿主机端口 |
| --- | ---: | ---: |
| 后端 | 8080 | 8080 |
| 前端 Nginx | 80 | 5173 |
| MySQL | 3306 | 3307 |
| Redis | 6379 | 6380 |
| RabbitMQ | 5672 | 5673 |
| RabbitMQ 管理台 | 15672 | 15673 |
| MinIO API/Console | 9000/9001 | 9000/9001 |

数据库迁移由 Spring Boot 启动时的 Flyway 执行，当前仓库存在 `V1__init_schema.sql` 至 `V8__add_notification_delivery_and_permissions.sql`，最新验证版本为 V8。MySQL 保存业务事实和附件元数据，Redis 负责会话标记、提醒索引和锁，RabbitMQ 负责异步提醒/通知，MinIO 保存附件内容。

## 4. 阶段0基线命令与运行态证据

### 4.1 命令结果

| 命令 | 本阶段结果 | 证据边界 |
| --- | --- | --- |
| `\.\mvnw.cmd test` | **通过：64 项执行，失败 0，跳过 1** | 唯一跳过为默认关闭的 `Stage14ContainerEnvironmentTest`；日志包含测试用 Spring Security 生成密码警告，不代表项目凭据泄露 |
| `Set-Location frontend; npm run build` | **通过** | TypeScript/Vite 构建成功；主 JS chunk 约 1.16MB，保留既有大包警告 |
| `docker compose config --quiet` | **本阶段未重新验证** | 由于 Windows 审批通道连续传输失败，命令未获得可靠退出结果；此前验收记录为通过，当前 `docker compose ps` 仍显示运行态健康 |
| `kubectl kustomize k8s` | **通过** | Kubernetes 清单可静态渲染 |

### 4.2 非破坏性 Compose 健康检查

本阶段只执行 `docker compose ps`，未重启、重建、删除服务或删除卷。当前六个服务均为 `healthy`：

| 服务 | 状态 | 端口证据 |
| --- | --- | --- |
| backend | healthy | `0.0.0.0:8080->8080/tcp` |
| frontend | healthy | `0.0.0.0:5173->80/tcp` |
| mysql | healthy | `0.0.0.0:3307->3306/tcp` |
| redis | healthy | `0.0.0.0:6380->6379/tcp` |
| rabbitmq | healthy | `5673->5672`、`15673->15672` |
| minio | healthy | `9000/9001` |

### 4.3 非破坏性 Kind 健康检查

当前 context 为 `kind-dev`，节点 `dev-control-plane` 为 `Ready`；`taskflow` 命名空间中 backend Deployment 为 `2/2`、frontend Deployment 为 `1/1`，三个应用 Pod 均为 Running、重启次数为 0。本阶段未重新部署或删除 Pod。

## 5. 当前 P0/P1/P2

以下优先级针对“生产发布门禁”；不否定本地学习和演示范围已通过的证据。

### P0——生产发布阻断

1. 开发凭据、默认回退值和本地 Kubernetes Secret 模板仍存在；需要 Secret Manager/Sealed Secrets、默认值拒绝和轮换演练。
2. Token 使用 `localStorage`，本地 HTTP/WebSocket 明文，Actuator/OpenAPI 和管理面生产边界尚未完成；需要 HttpOnly Cookie + CSRF 或 CSP/XSS 基线，以及 TLS/WSS 和管理面隔离。
3. MySQL、Redis、RabbitMQ、MinIO 仍是单实例；Kind 只托管前后端应用层，不构成生产高可用。

### P1——高风险工程缺口

1. 登录已有按账号/来源窗口限流，但尚无账号锁定和验证码升级。
2. 尚未完成在线 Maven/npm/OWASP 依赖扫描、覆盖率门槛和统一 CI 质量门禁。
3. 目标环境尚未按相同数据规模重放阶段15性能基线。

### P2——质量完善项

1. 前端主包约 1.16MB，需要路由懒加载或手工分包。
2. 缺少完整浏览器 E2E，尤其是登录、任务写入、401/403 和 WebSocket。
3. 附件病毒扫描、内容安全审核和压缩炸弹防护仍未实现。

## 6. 当前八维评分冻结

| 维度 | 权重 | 得分 | 加权分 |
| --- | ---: | ---: | ---: |
| 需求覆盖与核心功能 | 15% | 86 | 12.90 |
| 架构与可解释性 | 15% | 86 | 12.90 |
| 安全与数据保护 | 15% | 66 | 9.90 |
| 测试、构建与回归 | 15% | 85 | 12.75 |
| 评估可信度与可观测性 | 15% | 78 | 11.70 |
| 运行集成与恢复就绪度 | 15% | 76 | 11.40 |
| 工程治理 | 5% | 58 | 2.90 |
| 文档与交付可信度 | 5% | 92 | 4.60 |
| **合计** | **100%** |  | **79.05 → 79/100** |

本阶段评分冻结为 **79/100**，不因测试通过、Compose 健康或 Kind 单节点健康而上调。阶段后续只有在新增证据满足对应完成条件后，才重新评估相关维度。

## 7. 阶段1～7调优待办矩阵

| 阶段 | 目标 | 主要证据 | 完成条件 | 是否影响评分 |
| --- | --- | --- | --- | --- |
| 1 | 生产安全基线：Secret、Token、TLS/WSS、Actuator/OpenAPI 边界 | 配置拒绝默认值测试、Secret 外部化记录、HTTPS/WSS 或安全代理烟测、敏感信息扫描 | 默认开发凭据不能误启动；管理面有明确权限/网络边界；认证、登出、CSRF/XSS 和 TLS 证据留档 | 是，优先影响安全维度和 P0 门禁 |
| 2 | 工程治理和 CI 质量门禁 | CI 实际运行记录、Maven/npm 测试、覆盖率、静态检查、依赖扫描报告 | push/PR 可复现执行；失败能阻断合并；报告不包含真实凭据；缓存继续使用 F 盘策略 | 是，影响工程治理、测试和评估可信度 |
| 3 | 浏览器 E2E 主链路 | 真实浏览器测试报告、截图/日志脱敏、Compose 环境登录和任务流程 | 登录、登出、任务创建/编辑/状态、401/403、附件入口、通知/WebSocket 至少有稳定测试；不把 Mock 当真实浏览器证据 | 是，影响测试、需求覆盖和运行集成 |
| 4 | 可观测性和运行态诊断 | 结构化日志、Trace ID、审计、Actuator 保护、指标采集和故障定位记录 | 能从请求到数据库/MQ/Redis 关联 Trace；敏感字段不落日志；健康、就绪、错误和消息积压有明确指标 | 是，影响安全、评估可信度和工程治理 |
| 5 | 故障恢复与消息/数据一致性演练 | Compose 服务重启、RabbitMQ 有限重试/死信、Redis 索引重建、MinIO 补偿、Kind Pod/rollout 报告 | 每类故障都有可重复命令、预期状态和恢复证据；不删除业务卷；明确本地单机与生产 HA 边界 | 是，影响运行集成与恢复就绪度 |
| 6 | 性能和查询基线复测 | 固定数据规模下的性能 JSON、运行时指标、EXPLAIN 前后对照 | 在修改索引/分页/缓存前后使用同一机器和参数；记录错误率、p95/p99、连接池和消息堆积；不外推生产容量 | 是，影响评估可信度和架构判断 |
| 7 | 最终高维验收与分数重评 | 全量回归、Compose/Kind/E2E/扫描报告、评分 JSON、README/CHANGELOG 同步 | 所有阶段证据可追溯；评分逐项说明增减；P0/P1/P2 重新排序；生产结论仍由门禁决定 | 是，决定最终评分和交付判定 |

## 8. 阶段0完成门禁与遗留限制

已满足：目录和入口已核实；Git、环境、端口、迁移、测试和部署事实已记录；Maven、前端、Kustomize 和运行态检查有结果；当前评分与 P0/P1/P2 已冻结；阶段1～7均有目标、证据、完成条件和评分影响。

未完全满足的命令级门禁：`docker compose config --quiet` 本阶段因 Windows 审批通道传输失败未获得可靠退出结果。此前验收已通过同一命令，且当前 Compose 六服务均为 `healthy`；下一次阶段执行前应优先补做该静态命令并记录新的退出结果。

本阶段没有调整评分，没有删除数据库、Docker 卷、Kind 资源或测试数据，也没有修改业务代码。
