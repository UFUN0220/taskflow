# 阶段 18：安全和质量审查

## 阶段 1 安全基线收敛补充（2026-08-09）

阶段 1 已完成并验证：

- `dev`/`test`/`prod` 配置分层；prod 对 JWT、数据库、RabbitMQ、MinIO、bootstrap admin 和 WebSocket 来源执行显式 Secret/弱值拒绝；
- `.env.example` 和 `k8s/secret.yaml` 不再提供可误用凭据，`.gitignore` 增加证书私钥和运行时 Secret 路径；
- Spring Security 和前端 Nginx 增加基础 Header/CSP，prod 默认只暴露健康探针并关闭 Swagger/OpenAPI；
- 保持 Bearer + STOMP CONNECT 的一致性，没有进行不完整的 Cookie 迁移；localStorage 风险仍明确保留；
- 66 项后端测试执行、0 失败、1 跳过；前端构建、Compose 模板解析、Kustomize 渲染通过；当前运行 Compose 登录、`/api/auth/me`、登出和旧 Token 401 烟测通过。

阶段 2 补充依赖风险可见性：官方 npm registry 的 `npm audit` 实际完成，当前 lockfile 报告 2 个 moderate、0 个 high/critical；OWASP/NVD 扫描已配置但本机超过 5 分钟未完成，未将其写成“漏洞为 0”。

审查范围覆盖认证授权、数据范围、输入校验、SQL 拼接、文件上传、敏感配置、日志、JWT、WebSocket、RabbitMQ 消费、幂等、事务、线程/定时任务、资源关闭、依赖和完整构建测试。

## 已修复

### 1. 审计来源地址不再信任客户端转发头

审计日志原先读取客户端可以伪造的 `X-Forwarded-For` 首个地址。现在记录 `HttpServletRequest.getRemoteAddr()`；如果以后部署在反向代理后，应通过明确的可信代理列表和网络边界处理转发地址，不能直接信任任意请求头。

### 2. 附件增加内容签名校验

附件仍保留大小、后缀和 MIME 类型校验，并新增：

- PDF 必须以 `%PDF-` 开头；
- PNG 必须匹配 PNG 文件签名；
- JPEG 必须匹配 JPEG 文件签名；
- 文本文件拒绝包含 NUL 字节。

该校验不是病毒扫描，也不能替代对象存储隔离或人工审核。

### 3. 清理文档中的示例管理员密码

认证文档不再写入可复制使用的管理员密码。现有数据库中的 `admin` 是应用管理员账号，不是 Windows 账号；此前按用户要求重置过密码，首次登录后应立即修改。

## 审查结论

| 检查项 | 结论 | 证据或边界 |
| --- | --- | --- |
| 越权 | 通过当前范围 | Controller 使用 `@PreAuthorize`，核心服务再次校验用户、项目、任务和数据范围；现有 RBAC/数据范围测试通过 |
| SQL 注入 | 未发现当前代码注入点 | MyBatis 使用 `#{}`，JdbcTemplate 使用参数绑定；动态 `IN` 仅由数量生成 `?` 占位符 |
| 文件上传 | 已加强 | 限制大小、后缀、MIME 和内容签名；仍不是恶意文件扫描 |
| 敏感配置 | 部分通过 | prod 已移除敏感默认回退并对弱值 fail-fast；本地 `.env`/Kubernetes Secret 仍需由外部安全注入和轮换 |
| 日志泄密 | 未发现密码/完整 Token 输出 | 日志主要记录 trace、资源 ID、消息 ID 和异常类型；全量异常堆栈仍需结合生产日志策略审查 |
| Token 安全 | 部分通过 | JWT 有签名、过期时间、唯一 jti 和 Redis 活动会话撤销；Compose 旧 Token 失效烟测通过；前端 Token 仍存在 `localStorage`，存在 XSS 后被读取的风险 |
| 消息重复 | 通过当前范围 | 通知按 `source_message_id` 幂等，消费失败有有限重试和死信记录 |
| 事务边界 | 通过当前范围 | 核心业务写操作有 `@Transactional`；MinIO 网络调用采用补偿状态，不把长时间对象存储调用包在数据库事务中 |
| 线程池 | 未发现自建无限线程池 | 使用 Spring 调度和 RabbitMQ listener；生产环境仍应显式核对并发、队列容量和关闭策略 |
| 资源关闭 | 当前路径基本通过 | MinIO 上传使用 try-with-resources；下载流交给 Spring `InputStreamResource` 响应链路管理 |
| 依赖质量 | 有静态分析警告 | `mvn dependency:analyze` 成功，但报告了 Spring 传递依赖和测试依赖的常见误报；未据此盲目删除依赖 |

## 未解决风险

1. 登录接口现已增加基于 Redis 的账号 + 来源地址失败窗口限制，默认 10 次/60 秒；本地烟测已验证第 11 次返回 429，仍需在真实攻击流量和代理网络边界下验证阈值、可信来源地址和验证码升级策略。
2. 前端访问令牌使用 `localStorage`，应评估迁移到 HttpOnly、Secure、SameSite Cookie 或更严格的 CSP。
3. 默认 JWT Secret、MySQL/RabbitMQ/MinIO 开发凭据仍存在本地模板和 Compose 回退配置中；这些值只能用于个人本地学习环境。
4. JWT 已通过 Redis 活动会话标记支持主动撤销；用户被禁用、会话过期或主动退出后，HTTP 和 WebSocket 鉴权均会拒绝旧会话。真实 Compose 旧 Token 失效烟测已通过。
5. Swagger/OpenAPI 和 Actuator 已在 prod 配置层收紧：prod 关闭 OpenAPI、仅暴露 health，Nginx 仅代理精确健康路径；尚未完成生产网络隔离实测。
6. 当前 HTTP/WebSocket 仍可使用本地明文连接；生产环境必须使用 TLS/WSS，并正确配置可信反向代理。应用默认不信任转发 Header。
7. Kubernetes 阶段只部署应用层，中间件仍是 Docker Compose 单实例；本地 `k8s/secret.yaml` 为空值模板，不代表生产 Secret 管理。
8. 附件没有病毒扫描、压缩炸弹识别、图像重编码或内容安全服务；当前仅允许受限类型并限制大小。
9. npm audit 已在官方 registry 实际执行并记录 2 个 moderate advisory；OWASP/NVD 依赖扫描仍受本机数据库更新时间/网络限制未完成，不能据此宣称 Maven 依赖为零风险。

## 不适合生产使用的部分

- 本地 `.env`/Kubernetes 外部 Secret 的生成、轮换和审计仍未完成；
- 未配置 TLS、集中式 Secret 管理和生产级 Token 存储策略；登录限流和 Token 撤销已实现本地代码路径，但仍需生产环境验证；
- MySQL、Redis、RabbitMQ、MinIO 单实例；
- 本地 Kubernetes Secret 外部注入和生产网络隔离仍未验证；
- 本地 dev profile 仍保留开发型 Actuator/Swagger 便利配置；
- 未接入杀毒或内容安全扫描的附件上传。

## 验证结果

- 最新 `./mvnw.cmd test`：66 项执行、0 失败、1 项可选 Testcontainers 测试按默认配置跳过；显式阶段 14 Testcontainers 测试已通过 1 项，并完成 8 条 Flyway 迁移。
- `npm run build`：通过；存在既有的单 JS chunk 大于 500KB 警告；
- `docker compose config --quiet`：无 `.env` 时按必填 Secret 约束 fail-fast；使用仅存在于当前进程的非敏感测试值解析通过；
- 阶段 1 后端单元/接口测试覆盖生产弱 Secret 拒绝和基础安全 Header；当前运行 Compose 容器完成认证烟测；由于工作区没有真实 `.env`，未用假值重建现有应用容器，避免把测试凭据写入运行环境；
- Compose 后端容器健康检查：通过；
- `GET http://localhost:8080/api/health`：HTTP 200；
- 未删除数据库、Docker 卷、现有账号或用户文件；真实登录-登出-旧 Token 失效和 10 次/60 秒限流烟测已通过。

## 下一步建议

1. 将 JWT 迁移到 HttpOnly Cookie 或建立严格 CSP/XSS 防护基线；
3. 使用 Secret Manager/Sealed Secrets/Vault，移除 Compose 生产回退凭据；
4. 为 Actuator 和 OpenAPI 增加专用观测权限或内网访问策略；
5. 在有网络和审批的环境执行 npm/Maven/OWASP 依赖漏洞扫描；
6. 生产化部署前补齐 TLS、备份恢复、审计留存、消息集群和对象存储安全策略。
