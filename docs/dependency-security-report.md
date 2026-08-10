# 依赖漏洞治理与 CI 门禁记录（阶段 11，2026-08-10）

本报告只记录本轮实际执行结果。`target/` 已被 Git 忽略；最终源码对应的原始 OWASP 报告副本为 `target/phase11-owasp-final.json` 和 `target/phase11-owasp-final.xml`。报告中的 CVE 命中是 Dependency-Check 的 CPE/NVD 最佳努力结果，hosted suppressions 下载失败时不能直接当作已确认可利用漏洞，也不能当作零漏洞。

## Stage 11.5B：精确治理当前状态（2026-08-11）

已冻结 `BEFORE_STAGE_11_5B` 基线：OSV 21 个受影响 Maven package、70 条漏洞（7 critical、27 high、27 medium、9 low），官方 SARIF artifact 为 `9070998290`。已完成 P0/P1/P2 初步归因，并区分 runtime 与 test scope；可复核清单见[依赖漏洞精确治理记录](dependency-vulnerability-remediation-2026-08-11.md)。

本轮仅提交 Spring Boot parent `3.4.8`→`3.5.16` 的 BOM 小批候选，没有手工覆盖 Spring Framework、Spring Security、Tomcat、Jackson 或 Netty，也没有添加 suppression。由于当前本地 Windows runner 的 PowerShell 子进程持续返回 `CreateProcessAsUserW: 5 (拒绝访问)`，本提交后的 Maven、OSV、Testcontainers、前端和 E2E 尚未执行；因此本阶段暂记 `IN_PROGRESS_PENDING_LOCAL_REGRESSION`，评分保持 85/100，不能宣称漏洞已解决，也不能开始 Stage 13。

## 阶段 12.3：确定性扫描收口（历史状态，已由阶段 12.4 重新定位）

当前状态：`SCAN_INFRA_NOT_CLOSED`。本阶段只改进扫描运行条件和结果分类，不修改 Stage12 reliability 测试、不升级依赖、不添加 suppression。

### 已确认的前一轮根因

远程 run `31385404134` / job `93444588147` 从 11:52:15Z 开始，OWASP 步骤 11:54:05Z 开始。在未配置 NVD API key 的情况下，日志显示 NVD 数据集 374,572 条，下载到 180,000 条（48%）时接近 job 的 `timeout-minutes: 30`，随后 runner 收到 shutdown signal，Maven exit 143；没有出现 NVD 403/429、OOM、hosted suppression 异常或真实 CVSS gate 输出。job conclusion 为 `cancelled`，因此最终分类为 `JOB_TIMEOUT_DURING_NVD_INITIALIZATION`，不是 `VULNERABILITY_GATE_FAILURE`。

### 本阶段配置

- `pom.xml` 改用 `<nvdApiKeyEnvironmentVariable>NVD_API_KEY</nvdApiKeyEnvironmentVariable>`，让 Dependency-Check 从环境变量读取 API key，不把 Secret 展开到 Maven 参数；仓库不保存 key。
- `integration-security` job timeout 调整为 60 分钟，避免把 Java/Testcontainers、npm 和冷启动 NVD 更新共同挤在 30 分钟内。
- 使用 `actions/cache/restore@v5` 与 `actions/cache/save@v5` 缓存 `${{ runner.temp }}/dependency-check-data`。key 为 `${{ runner.os }}-dependency-check-v12.1-data`，包含 runner OS 与 Dependency-Check major/minor 版本，restore key 允许同版本持续更新，不绑定业务 `pom.xml` 全量 hash。
- workflow 只输出 `NVD_API_KEY configured: true/false`，同时记录 cache hit、开始/结束时间、exit code、分类和扫描日志；不输出 Secret。
- 分类固定为 `SCAN_PASS`、`VULNERABILITY_GATE_FAILURE` 或 `SCAN_INFRA_FAILURE`。前两类都继续阻断 gate；没有报告或报告解析不出 CVSS 高危证据时，不得伪装成漏洞失败。
- 缺少 `NVD_API_KEY` 时，扫描步骤快速写入 `SCAN_INFRA_FAILURE` 和 exit code 78，再由统一 gate 阻断；不会继续执行一个不可控的未认证 NVD 冷启动。
- 正常完成、漏洞失败或可控基础设施失败时，artifact 都尝试上传报告、日志、exit/classification/cache/key-status 文件；硬 runner shutdown 仍可能在上传前终止整个 job，这是本轮已观测到的限制。

### 本地验证

```powershell
.\mvnw.cmd '-Psecurity-scan' '-DskipTests' '-DautoUpdate=false' `
  '-Ddependency-check.data.directory=F:\newinstall\maven-repository\org\owasp\dependency-check-data\11.0' verify
```

结果：报告 HTML/JSON/XML/SARIF 等完整生成，Maven exit 1；报告和 Maven 输出均存在 CVSS >= 7 命中，分类为 `VULNERABILITY_GATE_FAILURE`。这证明新的 API key 参数没有破坏插件执行，但不代表远程 NVD cache miss/hit 已验证。

### 远程验证记录

本次 workflow 修改推送后必须分别记录：

| Run | Cache | NVD_API_KEY | Update/scan duration | Reports | Classification |
|---|---|---|---|---|---|
| 第一轮（run `31394054175` / job `93472490228`） | miss；日志为 `Cache not found` | `NOT_SET`（仓库 Secret 列表为空） | OWASP 13:42:20Z–14:45:12Z；约 62 分钟后 runner shutdown | 无，cache/gate/upload 被取消跳过 | `SCAN_INFRA_FAILURE`；进程实际 exit 143，硬取消导致分类文件未能落盘 |
| 第二轮（run `31400580061` / job `93494241832`） | miss；日志为 `Cache not found` | `NOT_SET`（仓库 Secret 列表仍为空） | 13:55:22Z–13:55:23Z；缺 key 后约 1 秒快速失败 | 已上传 artifact `9067520875`（744,805 bytes） | `SCAN_INFRA_FAILURE`；exit 78，统一 gate 失败 |
| 真正热缓存验证轮 | 未执行；先决条件是配置 `NVD_API_KEY` 并完成一轮冷启动 | 待配置后记录 SET/NOT_SET | 待执行 | 待执行 | 待执行 |

远程日志同时确认：NVD 374,922 条记录从 13:43:06Z 开始下载，14:44:02Z 到 80,000（21%），14:45:12Z 收到 shutdown signal 并 exit 143；没有 403/429、OOM 或 CVSS gate 输出。因此第一轮不是漏洞门禁失败，而是无 API Key 冷启动被 runner 超时取消。第二轮证明缺少 key 时会快速、可观测地阻断并上传 artifact，但不产生 NVD 报告，也不等同于 cache-hit。当前 GitHub 仓库未配置 `NVD_API_KEY`，无法安全伪造热缓存证据。配置 Secret 后，应先触发一轮允许完成并保存 cache 的冷启动，再触发真正热缓存轮确认 `cache-hit=true`。

在两轮远程结果完成前，项目验收报告中的“OWASP 未完成”事实保持不变，评分继续保持 85/100；本阶段当前为外部 Secret 配置阻塞，不能进入阶段 13。

## 阶段 12.4：NVD 不可达条件下的替代门禁（当前状态）

本阶段不再申请、获取或依赖 `NVD_API_KEY`，也不把 NVD 冷缓存问题无限阻塞主 CI。OWASP Maven `security-scan` profile 保留，但在 GitHub Actions 中定位为 `SUPPLEMENTAL_NVD_REMOTE_BLOCKED`；只有恢复受信任本地数据缓存时才允许以 `-DautoUpdate=false` 做 supplemental scan。

主依赖漏洞门禁改为 Google 官方 OSV-Scanner reusable workflow `v2.5.0`，递归扫描仓库中的 `pom.xml` 与 `frontend/package-lock.json`，漏洞发现和扫描基础设施失败都必须阻断该 job，并上传 SARIF/官方扫描结果。Maven 传递依赖默认走 deps.dev 解析；OSV 当前不覆盖 Maven test scope，故 Testcontainers/Maven 回归继续独立保留。

第一次真实远程 OSV run 已完成：run `31408614816` / job `93520987366`，OSV-Scanner `v2.5.0`；`pom.xml` 发现 27 packages，`frontend/package-lock.json` 发现 220 packages，过滤 15 个本地/不可扫描包；21 个 Maven package 受影响、70 个漏洞（7 Critical、27 High、27 Medium、9 Low、0 Unknown）；SARIF artifact `9070667075` 已上传并进入 Code Scanning。scanner 完成但 reporter 因漏洞发现失败，job 为 `VULNERABILITY_GATE_FAILURE`，不是 `OSV_SCAN_INFRA_FAILURE`。若未来 OSV 官方数据源不可达，必须分类为 `OSV_SCAN_INFRA_FAILURE`，不得写成无漏洞。

本阶段不会修改依赖版本、添加 suppression 或降低 CVSS 门槛；OWASP 与 OSV 的 artifact-level 对照见[依赖漏洞归因与双扫描器对照](dependency-vulnerability-triage-2026-08-10.md)。评分继续保持 85/100，阶段 13 不启动。

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
