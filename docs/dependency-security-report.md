# 阶段 2 依赖安全与质量门禁记录（2026-08-09）

本文件只记录实际执行结果，不把扫描未完成写成“漏洞为 0”。报告中的机器可读临时文件位于 `target/`，该目录已被 Git 忽略；CI 会将相同类型的结果作为 workflow artifact 保存。

## Maven / JaCoCo

执行命令：

```powershell
.\mvnw.cmd verify
.\mvnw.cmd "-Dtaskflow.integration=true" verify
```

结果：

- JaCoCo HTML：`target/site/jacoco/index.html`；XML：`target/site/jacoco/jacoco.xml`；
- 快速回归：66 项执行，0 失败，1 跳过；
- 显式 Testcontainers：1 项通过，完成 MySQL、Redis、RabbitMQ、MinIO 启动和 8 条 Flyway 迁移；
- 覆盖率门禁通过：总行覆盖率 47.20%；
- 核心类门槛为 45%，当前纳入的认证、数据权限、任务、通知和提醒核心类均达到门槛；
- JaCoCo 只对可解释的代码覆盖设置门槛，没有为了提高数字新增无业务价值测试。

覆盖率门禁在 `pom.xml` 中由 JaCoCo `verify` 阶段执行：bundle 行覆盖率至少 30%，核心类行覆盖率至少 45%。

## npm audit

默认 registry 为本机既有的镜像源时执行：

```powershell
npm audit --audit-level=high --json
```

结果：命令到达 `npmmirror.com`，但该镜像的 audit API 返回 HTTP 404 / `NOT_IMPLEMENTED`，不能据此推断依赖安全状态。

使用官方 npm registry 重试：

```powershell
$env:npm_config_registry = "https://registry.npmjs.org"
npm audit --audit-level=high --json
```

结果：退出码 0；总计 2 个 moderate advisory，high 0，critical 0，low 0。原始 JSON 写入未跟踪的 `target/npm-audit-official.json`。这只是当前 lockfile 和 registry 数据库时间点的结果，不是永久无漏洞保证。

## OWASP Dependency-Check / NVD

Maven 已新增 `security-scan` profile：

```powershell
.\mvnw.cmd -Psecurity-scan -DskipTests verify
```

本机实际执行超过 5 分钟后超时，未生成 `target/dependency-check-report.*`，因此本阶段不能声称 Maven/NVD 扫描成功，也不能声称漏洞为 0。GitHub Actions 中该步骤保留为 advisory，并上传报告（若生成）；网络或 NVD 数据库不可用时 workflow 不会伪造成功结果。

## 门禁分层

| 检查 | 本地命令 | CI workflow | 失败策略 |
| --- | --- | --- | --- |
| 后端快速回归 | `mvnw.cmd test` | `fast-check` | 阻断合并 |
| 前端类型检查/构建 | `npm run typecheck` / `npm run build` | `fast-check` | 阻断合并 |
| Compose/Kustomize 静态校验 | `docker compose config --quiet` / `kubectl kustomize k8s` | `fast-check` | 阻断合并 |
| Testcontainers + JaCoCo | `mvnw.cmd "-Dtaskflow.integration=true" verify` | `integration-security` | 阻断该 workflow；是否阻断合并由仓库分支保护决定 |
| npm audit | `npm run audit:ci`，必要时切换官方 registry | `integration-security` | 当前 advisory，网络/数据库和 moderate 风险需人工处理 |
| OWASP/NVD | `mvnw.cmd -Psecurity-scan -DskipTests verify` | `integration-security` | 当前 advisory；CVSS 高风险配置为失败条件，但 NVD 不可达不宣称通过 |

## 当前限制

- 本机没有安装 `actionlint`，workflow YAML 已通过 PyYAML 语法解析，但尚未完成 actionlint 级语义检查；
- GitHub Actions 尚未在远程仓库实际运行，当前记录是仓库内 workflow 配置和本地静态/命令证据；
- 前端生产 bundle 仍有约 1.16 MB 的既有 chunk warning；
- 依赖扫描没有自动升级依赖，后续应根据 advisory 的具体路径进行人工评估和单独变更。
