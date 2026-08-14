# TaskFlow Platform

企业任务协同与流程管理平台，面向本地学习、演示和 Java 后端校招面试。项目采用 Java 17 + Spring Boot + React/TypeScript 的模块化单体架构，核心事实由 MySQL 保存，Redis、RabbitMQ、MinIO 分别承担索引/锁、异步消息和附件内容职责。

## 核心能力

- 用户、部门、角色、权限和数据范围；
- 项目/任务、状态机、乐观并发控制、评论和附件；
- Redis 提醒索引、RabbitMQ 有限重试/死信/幂等通知；
- HttpOnly Cookie + CSRF、审计、STOMP/WebSocket 实时通知和断线补拉；
- Docker Compose 六服务、Kind 应用层部署、Testcontainers 和 Playwright 验收。

## 快速开始

完整 Compose：

```powershell
.\scripts\init-compose.ps1 -Rebuild
```

本地开发：

```powershell
.\mvnw.cmd spring-boot:run
Set-Location frontend
npm install
npm run dev
```

验收环境需要通过当前终端或 CI Secret 注入 `TASKFLOW_ACCEPTANCE_*` 变量：

```powershell
.\scripts\acceptance-up.ps1
.\scripts\acceptance-check.ps1
Set-Location frontend
npm run e2e
```

密码、JWT、Cookie 和 Secret 不写入仓库；acceptance 测试账号不会在 prod profile 创建。

## 验证命令

```powershell
.\mvnw.cmd test
Set-Location frontend
npm ci
npm run typecheck
npm run build
npm audit --registry=https://registry.npmjs.org
npm audit --omit=dev --registry=https://registry.npmjs.org
Set-Location ..
docker compose config --quiet
F:\newinstall\kubectl.exe kustomize k8s
```

真实容器集成：

```powershell
.\mvnw.cmd "-Dtaskflow.integration=true" verify
```

PowerShell 中必须引用 `-Dtaskflow.integration=true`，否则 Maven 可能把 `.integration=true` 当成生命周期阶段。CI 分为 `fast-check` 和 `integration-security`，OSV-Scanner 是当前主依赖漏洞门禁，OWASP/NVD 是 supplemental，不代表零供应链风险。

## 最终验收边界

最终正式评分为 **83/100**。最终 acceptance Chromium backend direct 与 Nginx proxy 各 `19/19`；远程 Testcontainers、JaCoCo、npm audit、OSV 和 CI 门禁通过。WebSocket 仍是 best-effort 实时推送，C4 服务器 transport 边界未建立，P1 为 `OPEN_WITH_DOCUMENTED_LIMIT`。

Docker Compose 和 Kind 单节点结果不等于生产 HA；单机性能不等于生产容量；生产 TLS、密钥轮换、跨实例 WebSocket、云 Ingress 和托管中间件仍未验证。

## 文档

- [文档索引](docs/README.md)
- [项目概览](docs/01-overview.md)
- [架构与核心设计](docs/02-architecture.md)
- [开发历史](docs/03-development-history.md)
- [测试与质量](docs/04-testing-and-quality.md)
- [性能与可观测性](docs/05-performance.md)
- [环境与部署](docs/06-environment-and-setup.md)
- [安全与依赖治理](docs/07-security-and-dependencies.md)
- [最终验收](docs/08-final-acceptance.md)
- [面试材料](docs/09-interview-materials.md)
- [环境清理报告](docs/environment-cleanup-report-2026-08-14.md)
- [原始证据迁移映射](docs/documentation-consolidation-map.md)

详细 acceptance 变量、依赖报告、性能原始数据、故障注入报告和 Playwright 失败证据仍保留在 `docs/`、`evaluation/`、`frontend/test-results/` 与 `scripts/`，详见索引。
