# TaskFlow Platform 确定性验收环境

## 背景

阶段 7 的管理员登录、Playwright 完整 E2E 和同参数性能复测曾依赖现有数据库中的 admin 密码。该密码既不能从日志猜测，也不应写入 Git，因此只能完成无凭据的接口/构建验证，无法形成可重复的验收闭环。

阶段 8 建立了一个与普通开发 Compose 隔离的 acceptance 环境。它使用同一套后端、前端和基础设施代码，但使用 `acceptance` Spring profile、专属 Compose 项目/命名卷和环境变量凭据。每次启动都会将验收管理员密码重新 BCrypt 哈希后写入 acceptance 数据库，避免依赖上次运行留下的密码。

## 组件和边界

入口文件：

- `docker-compose.acceptance.yml`：MySQL、Redis、RabbitMQ、MinIO、backend、frontend 六服务；端口默认为前端 `15173`、后端 `18080`，与普通 Compose 隔离。
- `src/main/resources/application-acceptance.properties`：只在 `acceptance` profile 激活，并要求 JWT、数据库、消息和对象存储配置显式存在。
- `AcceptanceAdminInitializer`：只在 `acceptance` profile 创建/对齐验收管理员并授予 `system_admin`；`DefaultAdminInitializer` 通过 `@Profile("!acceptance")` 排除在 acceptance 外。
- `scripts/acceptance-up.ps1`：校验变量、静态校验 Compose、构建并启动服务；不执行 `down -v`。
- `scripts/acceptance-check.ps1`：不输出 Token 或密码，执行 acceptance smoke。
- `scripts/acceptance-down.ps1`：停止容器但保留所有 acceptance 卷。

这仍是本机/CI 的确定性验收环境，不等于生产 HA、云数据库、托管消息服务或真实密钥管理平台。

## 必需环境变量

以下变量必须由当前进程、CI Secret 或外部 Secret 管理器注入。`.env.example` 只保留变量名和空值，不提供可登录密码。

```text
TASKFLOW_ACCEPTANCE_DB_ROOT_PASSWORD
TASKFLOW_ACCEPTANCE_DB_USERNAME
TASKFLOW_ACCEPTANCE_DB_PASSWORD
TASKFLOW_ACCEPTANCE_RABBITMQ_USERNAME
TASKFLOW_ACCEPTANCE_RABBITMQ_PASSWORD
TASKFLOW_ACCEPTANCE_MINIO_ROOT_USER
TASKFLOW_ACCEPTANCE_MINIO_ROOT_PASSWORD
TASKFLOW_ACCEPTANCE_JWT_SECRET       # 至少 32 个字符
TASKFLOW_ACCEPTANCE_ADMIN_USERNAME
TASKFLOW_ACCEPTANCE_ADMIN_PASSWORD   # 至少 12 个字符
TASKFLOW_ACCEPTANCE_TEST_USER_PASSWORD # 至少 12 个字符
```

可选变量包括 `TASKFLOW_ACCEPTANCE_BASE_URL`、各服务宿主机端口、`TASKFLOW_ACCEPTANCE_ADMIN_EMPLOYEE_NO` 和 `TASKFLOW_ACCEPTANCE_ADMIN_DISPLAY_NAME`。不要在 README、脚本、迁移或日志中填写这些变量的实际密码。

## 本地运行

在仓库根目录，并在同一 PowerShell 进程中完成 Secret 注入：

```powershell
.\scripts\acceptance-up.ps1
.\scripts\acceptance-check.ps1

Set-Location frontend
npm run e2e
Set-Location ..

python tools/performance/performance_harness.py prepare --output docs/performance-acceptance-prepare.json
python tools/performance/performance_harness.py run --output docs/performance-acceptance-runtime.json

.\scripts\acceptance-down.ps1
```

性能脚本保持原有 `--departments 10 --users 100 --tasks 1000 --concurrency 20 --warmup-seconds 10 --duration-seconds 60` 默认参数；它与 Playwright 使用同一套 acceptance 管理员和测试用户密码。性能数字只代表本机/本次 CI runner 条件。

## CI 使用

CI 应将上表变量配置为 repository/environment secrets，在 job 进程中导出后执行：

```text
docker compose -f docker-compose.acceptance.yml config --quiet
pwsh -File scripts/acceptance-up.ps1
pwsh -File scripts/acceptance-check.ps1
cd frontend && npm run e2e
```

CI 不应把 `.env`、Compose 展开结果、容器环境 dump 或 Playwright 调试日志作为 artifact 上传。若 E2E 失败，只上传不含凭据的 trace/screenshot/video。

## 安全证明和限制

- `application-acceptance.properties` 没有明文密码；管理员和测试用户密码由 Spring 配置绑定从环境变量取得，缺失时启动失败。
- `DefaultAdminInitializer` 不在 `acceptance` profile 中加载；acceptance 初始化器不在 prod/dev 中加载，因此不会通过本阶段新增逻辑创建或修改正式管理员。
- 初始化器只重置专属 acceptance 用户的密码哈希和管理员角色。建议使用独立用户名；如果操作者在隔离 acceptance 数据库中选择 `admin`，也不会接触普通 Compose 的数据库卷。
- smoke 只打印通过项和 HTTP 状态，不打印密码、JWT 或完整 Authorization Header。
- acceptance 卷与普通 Compose 卷分离；停止脚本不删除卷。需要清理时必须由操作者明确执行 Docker 的卷清理操作。
- acceptance 浏览器认证使用 HttpOnly `TASKFLOW_ACCESS` Cookie；`/api/auth/csrf` 提供 CSRF token，写请求提交 `X-XSRF-TOKEN`。该环境验证 Cookie/CSRF 行为，但不解决集中式 Secret 管理、TLS 证书轮换、Ingress HA 或多节点故障恢复。

## 当前验证记录

2026-08-09 本机实际结果：

- acceptance 六服务使用专属卷启动并通过健康检查；首次尝试发现原 Dockerfile 在容器内在线下载 Maven 依赖会因网络传输中断超时，随后改为先由项目 Maven Wrapper 构建 JAR，再使用 `Dockerfile.acceptance` 构建 JRE 镜像，后续启动成功。
- `scripts/acceptance-check.ps1` 通过 health、管理员登录、`/api/auth/me`、任务列表、登出和旧会话 401。
- 性能工具使用同一组 acceptance 变量完成 1 部门、1 用户、1 任务的数据准备，证据为 `docs/performance-acceptance-prepare-2026-08-09.json`；没有把它包装成完整性能复测。
- 历史 2026-08-09 运行曾为 6/9；阶段 10 修正任务编号、通知内容断言和 WebSocket Principal 链路后，2026-08-10 在同一类 acceptance 环境真实 Chromium 连续 3 次完成 9/9（27/27）。详细证据见 `docs/e2e-browser-report-2026-08-10.md`。
- 阶段 9 后默认后端回归为 75 项执行、0 失败、1 项可选集成测试跳过；显式 Testcontainers verify 为 75 项执行、0 失败、0 跳过；前端 typecheck/build、普通/acceptance Compose config 和 Kustomize 静态校验通过。
- `acceptance-down.ps1` 已执行，容器和网络停止/移除，但未使用 `-v`，acceptance 数据卷保留。

2026-08-10 阶段 9 追加验证：

- 因旧 acceptance MySQL 卷保留了上一轮初始化账号，未删除旧卷；本次使用新的 Compose 项目名和新隔离卷启动，六服务健康，旧卷保持不变。
- acceptance smoke 通过：HttpOnly/SameSite Cookie 登录、Cookie `/api/auth/me`、任务列表、CSRF 登出和登出后旧会话 401；未打印密码、JWT 或 Cookie 值。
- Playwright 使用同一 acceptance 账号运行，现已通过登录、401、普通用户 403、任务真实写链路、重复提交、登出、附件、真实 STOMP 通知和断线补拉；测试仍保持失败时在 `frontend/test-results/` 保存截图/视频/trace 的配置。

这些是当前 Windows/Docker Desktop 环境证据，不等于生产 HA、云服务或真实 Secret Manager 验证。
