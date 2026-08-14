# 安全与依赖治理

## 认证与管理面

正式 React 使用 HttpOnly `TASKFLOW_ACCESS` Cookie + CSRF Header；prod 默认 `Secure=true`、合理 SameSite，登录 JSON/Bearer 兼容接口仅服务脚本和集成测试。Redis 活动会话支持 logout 撤销和过期校验。prod 缺少 JWT、数据库、RabbitMQ、MinIO 或 bootstrap admin Secret 时 fail-fast。

Actuator、OpenAPI/Swagger 和 forwarded headers 按 profile 和可信代理边界管理；不信任任意 `X-Forwarded-*`，不把隐藏前端入口当安全控制。

## 依赖治理

最终 Log4j2 通过统一属性从 `2.24.3` 固定到 `2.25.5`，解析出的 `log4j-api` 和 `log4j-to-slf4j` 无混用。官方 npm audit 及生产 audit 为 0；远程 OSV v2.5.0 对当前 Maven/npm 快照输出 `No issues found`。

这只代表当前依赖快照通过 OSV，不等于零供应链风险；OWASP/NVD 仍标记为 `SUPPLEMENTAL_NVD_REMOTE_BLOCKED`，不把历史数据源失败或本地 Docker 扫描阻塞写成“扫描通过”。详细版本树、报告分类和历史告警见 `dependency-security-report.md`。

## 未关闭风险

生产 TLS、集中密钥管理和轮换、账号风控升级、可信代理真实部署验证、附件病毒扫描、跨实例 WebSocket 广播和生产级基础设施 HA 仍未完成。
