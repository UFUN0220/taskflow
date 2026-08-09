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
- 阶段 7 快速回归：67 项执行，0 失败，1 跳过；
- 阶段 7 显式 Testcontainers：67 项执行，0 失败，0 跳过，完成 MySQL、Redis、RabbitMQ、MinIO 启动和 8 条 Flyway 迁移；
- 覆盖率门禁通过：最终 JaCoCo 总行覆盖率 47.70%；
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

阶段 7 使用官方 registry 和高危门槛执行：退出码 0；总计 2 个 moderate advisory，high 0，critical 0，low 0。未使用 `--audit-level=high` 的全量命令会因 moderate advisory 返回退出码 1；这不是“零漏洞”。当前 advisory 影响 React Router/React Router DOM，存在可用修复提示，需单独评估升级。

## OWASP Dependency-Check / NVD

Maven 已新增 `security-scan` profile：

```powershell
.\mvnw.cmd -Psecurity-scan -DskipTests verify
```

阶段 7 实际执行约 2 分钟：NVD 数据库使用了近期缓存，但 hosted suppressions 下载连接重置；扫描仍生成了依赖检查输出并因 CVSS 阈值失败。输出包含 Kotlin、Netty、Spring Boot/Spring Framework/Spring Security、Tomcat 等高危告警，需按报告逐项核实。结果是“扫描执行但质量门禁失败”，不是“漏洞为 0”；在依赖升级或经审查的临时抑制前，不得把 security workflow 说成通过。

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
- GitHub Actions 已在远程执行过，但 integration-security 在 Maven Wrapper 权限阶段以 exit code 126 失败；本轮已修复，尚未获得修复后的远程重跑结果；
- GitHub runner 的 exit code 126 根因是 Git 中 `mvnw` 原先为 mode `100644`，workflow 已改为 `bash ./mvnw`，并同步修复 wrapper 的 Unix executable bit；随后又发现 `.mvn/maven.config` 硬编码 Windows `F:\newinstall` settings，现已改为跨平台 `.m2-local/repository`，CI job 清空 `MAVEN_ARGS/MAVEN_OPTS` 并使用 runner 临时 Maven 用户目录。Action 已迁移到 checkout/setup-java/setup-node 的 Node 24 版本线，artifact upload 使用 Node 24 版本线。
- 前端最终最大共享 chunk 约 747.99 KB，仍有 Vite 500 KB warning；
- 当前依赖扫描没有自动升级依赖；需要人工核实 OWASP/NVD 结果是否存在 CPE 误报，再通过单独依赖升级变更和完整回归关闭告警。
