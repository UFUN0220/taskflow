# 测试与质量

## 本地快速回归

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

PowerShell 中 `-Dtaskflow.integration=true` 必须整体加引号，避免被 Maven 解析为错误 lifecycle phase。

## 后端与容器

当前默认 Maven 测试为 85 tests、0 failures、0 errors、5 skipped；显式 integration verify 的远程真实结果为 85/0/0/0。Stage12 Testcontainers 为 4/4，Stage14 为 1/1，Flyway V1–V8 与 JaCoCo 门禁通过。默认测试和容器测试分离，不能把 skipped 误写成通过。

Stage12 自动化覆盖 Rabbit retry/DLQ/replay/幂等、Redis 派生索引重建、MinIO 成功/失败补偿和 MySQL restart/Flyway/事实快照；这些结果来自远程真实 Docker 运行，仍不等于生产 HA。

## 浏览器 E2E

Playwright 使用 acceptance 环境变量，不保存密码。最终 SHA 的 Chromium 证据：

| 路径 | 结果 | 关键事实 |
|---|---:|---|
| backend direct | 19/19 | 登录、401/403、任务写链路、重复提交、登出、附件、真实 STOMP MESSAGE、断线补拉 |
| Nginx proxy | 19/19 | 同上，真实经过 frontend Nginx `/ws` |

历史 `4/9`、`6/9`、`8/9` 和后续 `9/9` 均保留在原始报告中。测试失败会保留 screenshot、video 或 trace；WebSocket 是浏览器真实帧验证，不是后端 mock。

## CI 与依赖质量

GitHub Actions 分为 fast-check 和 integration-security。最终远程两个 workflow 成功；OSV-Scanner v2.5.0 输出 exit 0/`No issues found`。OWASP/NVD 仍属于 supplemental，不因外部数据源限制而被写成零漏洞。

## 质量边界

本项目没有把 Mock、单机 Compose、Kind 单节点、OSV 当前快照或单机性能基线包装为生产能力；测试数字必须保留执行环境、时间和命令来源。
