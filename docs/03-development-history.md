# 开发历史与阶段演进

本文把原有 Stage/Phase 报告按大阶段归并；历史失败不被覆盖，原始证据仍按迁移映射保留。

## Phase I：基础、需求与架构（阶段 0–3）

- 冻结项目目录、技术栈、Compose/Kubernetes 边界和验收规则；
- 建立认证、角色、部门、项目任务基础模型、Flyway 迁移和模块化单体边界；
- 早期浏览器 E2E 曾出现 `4/9`、`6/9` 和订阅失败，保留为失败基线；后续通过 acceptance 环境、订阅 READY 确认和真实 Chromium 回归修复。

## Phase II：核心业务与一致性（阶段 4–9）

- 完成组织权限、项目任务、查询分配、状态机与乐观锁、评论附件、提醒计划；
- Redis 只做提醒索引/锁，MySQL 保留事实；MinIO 与附件元数据采用明确状态和补偿语义；
- 阶段 8 建立确定性 acceptance 环境，凭据由环境变量注入，不猜测现有管理员密码；阶段 9 将正式 React 认证迁移到 HttpOnly Cookie + CSRF，保留脚本 Bearer 兼容接口。

## Phase III：异步通知、浏览器闭环与安全治理（阶段 10–12.4）

- RabbitMQ 通知链路加入事务提交后发送、手动 Ack、有限重试、死信和幂等；
- WebSocket Principal、`/user` destination、断线补拉和 Nginx `/ws` 代理逐步验证；历史 proxy-only 失败记录保留；
- 建立 CI、JaCoCo、npm audit、OWASP supplemental 和 OSV 主门禁；NVD 不可达时不伪造扫描通过。

## Phase IV：性能、部署与最终封板（阶段 13–19）

- 完成前端路由懒加载、性能数据准备、EXPLAIN、运行时指标和本机基线；入口 chunk 下降不被描述为总 bundle 或生产性能提升；
- 完成 Compose 六服务和 Kind 应用层部署验证，明确单节点边界；
- 最终安全封板把 Log4j2 `2.24.3 → 2.25.5`，远程 fast-check/integration-security/OSV 通过；最终 acceptance direct/proxy 各 `19/19`；
- C4 服务器 transport 边界因未形成稳定证据而撤回，WebSocket P1 保持 `OPEN_WITH_DOCUMENTED_LIMIT`。

## 关键历史限制

同参数性能复测只能代表 Windows 11 单机条件；生产级 TLS、密钥轮换、跨实例 WebSocket、云 Ingress、跨节点 HA 和在线扫描数据源新鲜度仍需独立验证。评分演进和完整证据见[最终验收](08-final-acceptance.md)及原始证据目录。
