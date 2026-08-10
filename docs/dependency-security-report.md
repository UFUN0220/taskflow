# 依赖漏洞治理与 CI 门禁记录（阶段 11，2026-08-10）

本报告只记录本轮实际执行结果。`target/` 已被 Git 忽略；最终源码对应的原始 OWASP 报告副本为 `target/phase11-owasp-final.json` 和 `target/phase11-owasp-final.xml`。报告中的 CVE 命中是 Dependency-Check 的 CPE/NVD 最佳努力结果，hosted suppressions 下载失败时不能直接当作已确认可利用漏洞，也不能当作零漏洞。

## 1. 治理前后摘要

| 检查 | 治理前 | 治理后 | 结论 |
| --- | --- | --- | --- |
| npm official audit | 2 moderate，high 0，critical 0 | moderate 0，high 0，critical 0 | 通过；命令退出 0 |
| OWASP Dependency-Check | 扫描失败，hosted suppression reset，报告高危命中 | 最终源码扫描仍失败；19 个依赖条目、215 条 vulnerability record，其中按报告 CVSS≥7 的记录 79 high、25 critical | 未通过；不能宣称 Maven 依赖清零 |
| CI fast-check | 已有后端/前端/Compose/Kustomize 快速检查 | 保持两层 workflow，未增加扫描工具 | 静态 YAML 可解析 |
| CI integration-security | 扫描步骤 `continue-on-error`，未形成最终门禁 | npm 高危/严重和 OWASP 扫描失败统一由 final gate 阻断 | 本地改动，远程尚未验证 |

## 2. npm audit 明细

执行前保存：`target/phase11-npm-audit-before.json`。命令：

```powershell
Set-Location frontend
npm ci
npm audit --registry=https://registry.npmjs.org --json
```

升级前锁定版本为 `react-router-dom@6.30.4`，`react-router@6.30.4` 为其间接依赖。审计报告逐项为：

| 包 | 版本/依赖性质 | Advisory | 严重级别 | 项目使用 | 修复与风险 |
| --- | --- | --- | --- | --- | --- |
| `react-router` | 6.30.4，间接；由 `react-router-dom` 引入 | `GHSA-wrjc-x8rr-h8h6`，CVE-2025-68470；`GHSA-337j-9hxr-rhxg` | moderate | React Router 实际用于前端路由 | 升级到 7.18.2；跨 major 兼容风险通过 typecheck、build 和 E2E 验证 |
| `react-router-dom` | 6.30.4，直接依赖 | `GHSA-jjmj-jmhj-qwj2` | moderate | 实际用于 SPA 路由 | 直接固定到 7.18.2；升级中间版本 7.18.0 暴露 `GHSA-qwww-vcr4-c8h2` high，因此没有停在 7.18.0 |

升级后保存：`target/phase11-npm-audit-after.json`。最终结果：`info=0, low=0, moderate=0, high=0, critical=0`，依赖总数 220，命令退出 0。未添加 npm suppression。

## 3. Maven / OWASP 明细

最终源码仍为 Spring Boot `3.4.8`，OkHttp 直接依赖从 `5.1.0` 升级到 `5.4.0`。曾试验 Spring Boot `3.5.16` 和同一 3.4 系列的 `3.4.13`，两者在全新 acceptance Compose 中均出现浏览器经 Nginx `/ws` 代理时只收到 `CONNECTED`、未完成订阅/MESSAGE 的回归，因此已回退；未将未经兼容验证的 BOM 升级留在最终代码中。

最终命令：

```powershell
.\mvnw.cmd -Psecurity-scan -DskipTests `
  -Ddependency-check.data.directory=F:\newinstall\maven-repository\org\owasp\dependency-check-data\11.0 `
  -DautoUpdate=false verify
```

实际结果：报告生成成功，但 Maven 以 exit 1 结束。使用的是本机已有缓存，`hosted suppressions` 为空且强制更新失败；`.NET Assembly Analyzer` 还报告本机没有 dotnet 8 runtime。最终 JSON 中 19 个依赖条目含漏洞记录，按报告 CVSS 统计为 79 条 high、25 条 critical。以下列出最终报告中的受影响组件与 CVE；同一组件的重复命中只在表中合并，不把重复 CPE 计成多个独立依赖。

| 组件/版本 | 直接性与实际用途 | CVE / 最高 CVSS | 当前处理 |
| --- | --- | --- | --- |
| `org.springframework.boot:spring-boot:3.4.8`；`spring-boot-starter-web:3.4.8` | starter/BOM 传递，运行时核心；starter-web 直接声明 | `CVE-2026-40974`, `CVE-2026-22731`, `CVE-2026-22733`, `CVE-2026-40972`, `CVE-2026-40975`, `CVE-2026-40973`, `CVE-2026-40977`；最高 9.8 | 未 suppression；Boot 3.4.13/3.5.16 试验因 WebSocket 代理回归回退，需单独升级专项 |
| `org.springframework:spring-core:6.2.9`；`spring-web:6.2.9` | Boot 传递，运行时 Web/API | `CVE-2026-41855`, `CVE-2026-41838`, `CVE-2026-41842`, `CVE-2026-41848`, `CVE-2026-41850`, `CVE-2026-41851`, `CVE-2026-22740`, `CVE-2026-41854`, `CVE-2026-41844`, `CVE-2026-41845`, `CVE-2026-41846`, `CVE-2026-22737`, `CVE-2026-41840`, `CVE-2026-41841`, `CVE-2026-41843`, `CVE-2026-22745`, `CVE-2026-41852`, `CVE-2026-41853`, `CVE-2026-41839`, `CVE-2026-22741`, `CVE-2026-22735`；最高 9.8 | 未 suppression；需在保持 STOMP 代理实链的前提下升级 |
| `org.springframework.security:spring-security-core/web:6.4.8` | Boot 传递，认证与授权实际运行时 | `CVE-2026-22732`, `CVE-2026-47838`, `CVE-2026-40988`, `CVE-2026-22748`, `CVE-2026-41706`, `CVE-2026-41003`, `CVE-2026-41694`, `CVE-2026-22751`, `CVE-2026-22746`；最高 9.1 | 未 suppression；需单独验证维护线升级 |
| `org.apache.tomcat.embed:tomcat-embed-core:10.1.43` | Boot 传递，实际 HTTP/WebSocket 容器 | `CVE-2026-41293`, `CVE-2026-43512`, `CVE-2025-55754`, `CVE-2025-66614`, `CVE-2026-29145`, `CVE-2026-43515`, `CVE-2026-53434`, `CVE-2026-55276`, `CVE-2026-59083`, `CVE-2026-59084`, `CVE-2025-48989`, `CVE-2025-55752`, `CVE-2026-24734`, `CVE-2026-24880`, `CVE-2026-29146`, `CVE-2026-34483`, `CVE-2026-34487`, `CVE-2026-41284`, `CVE-2026-43513`, `CVE-2026-66299`, `CVE-2026-42498`, `CVE-2026-53404`, `CVE-2026-34500`, `CVE-2026-55955`, `CVE-2026-55956`, `CVE-2026-25854`, `CVE-2026-50229`, `CVE-2025-61795`, `CVE-2026-24733`, `CVE-2026-43514`；最高 9.8 | 未 suppression；运行时组件，不能仅按“本地项目”忽略 |
| `io.netty:netty-transport:4.1.123.Final` | Redis/Lettuce 传递，运行时网络组件 | 报告列出 `CVE-2026-45674`, `CVE-2026-47691`, `CVE-2026-42581`, `CVE-2026-42579`, `CVE-2026-42584`, `CVE-2026-56820`, `CVE-2026-33871`, `CVE-2026-48006`, `CVE-2026-48059`, `CVE-2026-55851`, `CVE-2026-56745`, `CVE-2026-59901`, `CVE-2026-56817`, `CVE-2025-55163`, `CVE-2026-44249`, `CVE-2026-33870`, `CVE-2026-42583`, `CVE-2026-42585`, `CVE-2026-42587`, `CVE-2026-44248`, `CVE-2026-44250`, `CVE-2026-44890`, `CVE-2026-44891`, `CVE-2026-44893`, `CVE-2026-45416`, `CVE-2026-46340`, `CVE-2026-48043`, `CVE-2026-50010`, `CVE-2026-50011`, `CVE-2026-55831`, `CVE-2026-55833`, `CVE-2026-56819`, `CVE-2026-56821`, `CVE-2026-56822`, `CVE-2026-42586`, `CVE-2025-58057`, `CVE-2026-50560`, `CVE-2026-59899`, `CVE-2026-59900`, `CVE-2026-45673`, `CVE-2025-67735`, `CVE-2026-42580`, `CVE-2026-56746`, `CVE-2026-59920`, `CVE-2026-59921`, `CVE-2026-59898`, `CVE-2026-41417`, `CVE-2026-47244`, `CVE-2026-50020`, `CVE-2026-45536`, `CVE-2025-58056`, `CVE-2026-42578`；最高 10.0 | 未 suppression；需与 Boot/Netty BOM 一起升级 |
| `org.jetbrains.kotlin:kotlin-stdlib:1.9.25` | MinIO 传递的 OkHttp 路径，运行时/传递 | `CVE-2026-53914`, `CVE-2020-29582`；最高 9.8 | OkHttp 直接声明已升到 5.4.0，但 MinIO 仍带 `okhttp:5.1.0`，故未声称已修复；不手工覆盖 Kotlin |
| `org.apache.httpcomponents.core5:httpcore5:5.0.2` | Testcontainers docker-java shaded 传递，测试工具链 | `CVE-2026-54399`, `CVE-2026-54428`；最高 7.5 | 未 suppression；可评估 Testcontainers/docker-java 升级，当前未改测试拓扑 |
| `com.fasterxml.jackson.core:jackson-databind:2.18.4` | Boot 传递，运行时 JSON | `CVE-2026-54512`, `CVE-2026-54513`, `CVE-2026-54514`, `CVE-2026-54515`；最高 8.1 | 未 suppression；需随 BOM 升级并回归序列化契约 |
| `org.apache.logging.log4j:log4j-api:2.24.3` | Boot logging 传递，运行时日志 API | `CVE-2026-34478`, `CVE-2026-34479`, `CVE-2026-34480`, `CVE-2025-68161`, `CVE-2026-34477`, `CVE-2026-34481`, `CVE-2026-49844`；报告中最高达到 6.9 | 未 suppression；当前低于 7 的记录仍作为 advisory 跟踪 |
| `com.mysql:mysql-connector-j:9.3.0` | Boot 管理的 runtime 依赖，实际数据库驱动 | 本轮 JSON 命中 CVE-2026-60193/60192/60586/60317/60623/60624/61082 | 未 suppression；需核实 NVD/CPE 与可用 Connector/J 维护版本 |
| `org.hibernate.validator:hibernate-validator:8.0.2.Final` | validation starter 传递，运行时校验 | `CVE-2025-15104`；最高 5.3 | advisory，未 suppression |
| `org.apache.commons:commons-lang3:3.17.0` | MinIO/其他库传递，运行时 | `CVE-2025-48924`；最高 5.3 | advisory，未 suppression |
| `org.assertj:assertj-core:3.26.3` | `spring-boot-starter-test` 测试依赖 | `CVE-2026-24400`；报告命中但需核实是否为 CPE 误报 | 未 suppression；测试 scope，不计生产运行时已修复 |
| `org.xmlunit:xmlunit-core:2.10.3` | 测试依赖传递 | `CVE-2024-9410`；最高 5.3 | advisory，未 suppression |
| `org.webjars:swagger-ui:5.21.0` 内嵌 `DOMPurify@3.2.4` | OpenAPI 可选依赖；prod 默认关闭管理面，但 artifact 仍在 classpath | 报告列出多条 `CVE-2026-*`，包括 `CVE-2026-65898`；最高记录约 7.2，且出现 DOMPurify CPE 命中 | 未 suppression；应升级 springdoc/swagger-ui 或移除可选 artifact 后单独回归 |
| `org.springframework.amqp:spring-amqp:3.2.6` | RabbitMQ starter 传递，运行时消费者 | `CVE-2026-41714`；最高 4.0 | advisory，未 suppression |

以上条目来自最终报告的 `dependencies[].vulnerabilities[]`，不存在 GitHub GHSA 编号的 Maven 命中不补写 GHSA。由于 hosted suppression 下载失败，CPE 误报可能存在；本阶段没有新增 suppression 文件。

## 4. Maven 扫描配置与门禁

`pom.xml` 的 `security-scan` profile 现在：

- `failBuildOnCVSS=7`，覆盖 high/critical，而不是原来的只阻断 CVSS 9+；
- `failOnError=true`，NVD/报告生成失败也不能绿灯；
- `hostedSuppressionsEnabled=true`，不通过关闭 hosted suppression 来掩盖数据源问题；
- 支持 `-Ddependency-check.data.directory=...`，本地和 CI 可使用 F 盘/runner temp 缓存；
- 支持 `NVD_API_KEY` 环境变量映射，不把 API key 写入仓库或日志。

CI 的 `integration-security` 不再把 npm/OWASP 写成 advisory：两个扫描步骤先保存退出码和报告，最后的 `Enforce dependency security gate` 在 JSON 无效、扫描失败、npm high/critical>0 或 OWASP 非 0 时阻断 job。moderate 不单独阻断，但本轮 npm 已清零。

## 5. CI 与静态验证

已核实：

- `.github/workflows/fast-check.yml`、`integration-security.yml` 使用 checkout/setup-java/setup-node 的 Node 24 action 版本线；Java 17、Linux `bash ./mvnw`、临时 `MAVEN_USER_HOME` 和空 `MAVEN_ARGS/MAVEN_OPTS` 保留；
- 依赖扫描 workflow 没有增加扫描工具；使用现有 npm audit 和 OWASP Dependency-Check；
- PyYAML 静态解析：两个 workflow 均 `YAML_OK`；
- 本机没有 `actionlint`，因此 actionlint 结果为 `NOT_EXECUTED`；
- GitHub Actions 远程修复后的 integration-security 尚未从本环境重新触发/读取，状态为 `NOT_REMOTE_VERIFIED`，不能写成远程绿灯；
- Playwright 不在当前两个 CI workflow 中，原因是 workflow 没有 acceptance Secret/六服务启动步骤；本地 acceptance 仍按独立命令执行，不能把 CI 的 npm/build 通过等同为浏览器 E2E 通过。

## 6. 实际回归证据

| 命令 | 结果 |
| --- | --- |
| `npm ci` | 通过 |
| `npm audit --registry=https://registry.npmjs.org --json` | 通过，moderate/high/critical 均 0 |
| `npm run typecheck` | 通过 |
| `npm run build` | 通过；最大共享 chunk 767.18 KB，仍有 Vite 500 KB warning |
| `mvnw.cmd test`（最终依赖组合） | 77 run，0 fail，1 skip，通过 |
| `mvnw.cmd -Dtaskflow.integration=true verify`（最终依赖组合） | 77 run，0 fail，0 skip，Flyway V1–V8/Testcontainers 通过 |
| `mvnw.cmd -Psecurity-scan ... verify` | 报告生成，但 exit 1；这是预期的未治理风险证据 |
| 新 acceptance Compose smoke | Cookie/CSRF、`/me`、任务列表、登出旧会话失效通过 |
| 全新 acceptance + 后端直连 WSS | Chromium 9/9 通过；这是直连证据 |
| 全新 acceptance + 前端 Nginx `/ws` | 6/9；STOMP CONNECTED 可见，但未收到 MESSAGE，必须保留为未闭环 |

依赖升级过程中的 Spring Boot 3.5.16/3.4.13 试验均因 Nginx `/ws` 实链回归而回退；最终保留的 Boot 3.4.8 + OkHttp 5.4.0 组合没有因单元/Testcontainers 回归失败，但 Nginx 代理 WebSocket 问题仍是独立 P1。

## 7. 当前未解决项

- Maven/OWASP 仍未通过，且 hosted suppression/NVD 在线新鲜度未完全闭环；不能宣称依赖零漏洞。
- Spring Boot/Spring Framework/Security/Tomcat/Netty 的安全升级需要先修复 Nginx `/ws` 代理实链并重跑完整 Chromium、Testcontainers 和 Compose smoke。
- MinIO 传递的 `okhttp:5.1.0` 仍带 Kotlin 1.9.25；不能仅因直接 OkHttp 5.4.0 通过就说 Kotlin 告警已修复。
- `actionlint` 未安装；GitHub Actions 远程状态 `NOT_REMOTE_VERIFIED`。
- 测试 fixture 在 Playwright worker 因失败重启后复用同一 run ID 会遇到用户 409；这属于测试数据可重复性问题，当前阶段未改 E2E 业务逻辑。

阶段 12 未开始。
