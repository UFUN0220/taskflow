# 阶段 15/阶段 4：查询优化、压测与可观测性

## 交付边界

阶段 15 提供可重复的数据准备、HTTP 压测、运行时观测和 EXPLAIN 工具。阶段 4 在此基础上增加前端路由懒加载、更多 Actuator/RabbitMQ 指标采集和不依赖 Compose `.env` 的 Redis 容器采集。脚本不会预设或虚构 QPS、延迟、错误率、GC 或 Redis 命中率；这些数值必须在指定机器、指定数据规模和指定配置下实际运行后填写。

本阶段暂不修改业务索引或查询实现。先通过基线结果确认真实瓶颈，再决定是否需要联合索引、批量查询、缓存或深分页优化。

## 工具

- [performance_harness.py](../tools/performance/performance_harness.py)：使用 Python 标准库生成部门、用户、草稿任务，并运行登录、任务列表、任务详情、创建任务、状态更新、通知列表六类场景。
- [explain.sql](../tools/performance/explain.sql)：分析任务列表、任务详情、通知列表的执行计划和现有索引。
- [collect-runtime.ps1](../tools/performance/collect-runtime.ps1)：采集受认证保护的 HTTP、HikariCP、Executor、JVM、RabbitMQ Actuator 指标和 Redis `INFO stats`。RabbitMQ listener 指标若未暴露会记录 `unavailable`。

## 前置条件

1. Docker Compose 中的 MySQL、Redis、RabbitMQ、MinIO 已启动，且后端使用本地配置连接这些服务。
2. 后端已执行 Flyway 迁移并运行在 `http://localhost:8080`，且 `taskflow.auth.bootstrap-admin.enabled=true` 已用非空密码创建管理员。
3. 通过环境变量提供管理员凭据；密码不会写入脚本或结果文件：

```powershell
$env:TASKFLOW_PERF_ADMIN_LOGIN = "admin"
$env:TASKFLOW_PERF_ADMIN_PASSWORD = "replace-with-local-admin-password"
```

4. 后端的 `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE` 默认包含 `metrics`，指标端点仍受 Spring Security 保护，观测脚本应提供同一管理员 Token。

## 生成可配置数据

下面的示例生成 10 个部门、100 个用户和 1000 个草稿任务。数据使用 `PERF_DEPT_*`、`perf_user_*` 和 `PERF_TASK_*` 前缀，重复执行时会复用已生成的数据，不会清理或删除其他业务数据。

```powershell
python .\tools\performance\performance_harness.py prepare `
  --base-url http://localhost:8080 `
  --departments 10 `
  --users 100 `
  --tasks 1000 `
  --output docs\performance-prepared.json
```

## 执行基线

压测参数可按机器资源调整。`--warmup-seconds` 不计入统计，`--duration-seconds` 是正式采样时长，`--concurrency` 是并发工作线程数。

```powershell
python .\tools\performance\performance_harness.py run `
  --base-url http://localhost:8080 `
  --concurrency 20 `
  --warmup-seconds 10 `
  --duration-seconds 60 `
  --output docs\performance-baseline.json
```

也可以只运行指定场景：

```powershell
python .\tools\performance\performance_harness.py run `
  --scenarios task_list,task_detail,notification_list `
  --concurrency 10 `
  --duration-seconds 30 `
  --output docs\performance-read-only.json
```

脚本输出每个场景的请求数、成功数、错误数、错误率、QPS、平均响应时间、p95、p99 和 HTTP 状态码分布。`state_update` 会从 `PERF_TASK_*` 草稿任务中各取一次并提交；因此状态更新场景的预置任务量应覆盖预期请求量。

## 本轮实测基线（2026-08-09）

本轮在 Windows 11、16 CPU、Python 3.12.1、Docker Compose 本地单实例环境执行：10 个部门、100 个用户、1000 个预置任务；并发 20，预热 10 秒，正式采样 60 秒，随机种子 `20260807`。结果文件为 `docs/performance-prepared.json`、`docs/performance-baseline.json`、`docs/performance-runtime.json` 和 `docs/performance-explain.txt`。

| 场景 | 请求数 | 成功 | 错误率 | QPS | 平均 ms | p95 ms | p99 ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| login | 2332 | 2332 | 0 | 38.867 | 399.068 | 542.310 | 642.514 |
| task_list | 2294 | 2294 | 0 | 38.233 | 20.702 | 39.214 | 50.068 |
| task_detail | 2278 | 2278 | 0 | 37.967 | 15.264 | 31.453 | 43.969 |
| task_create | 2345 | 2345 | 0 | 39.083 | 45.371 | 78.465 | 102.575 |
| state_update | 722 | 722 | 0 | 12.033 | 61.069 | 102.322 | 124.869 |
| notification_list | 2323 | 2323 | 0 | 38.717 | 18.636 | 35.827 | 49.165 |

采样结束时 HikariCP 为 active=0、idle=10、pending=0、max=10；Redis `INFO stats` 已写入运行时报告。上述数字只代表本机、当前数据规模、当前配置和本次 60 秒窗口，不代表生产容量或长期稳定性。

## 运行时观测

先用压测脚本的登录结果取得管理员 Token，再在压测期间或结束后执行：

```powershell
$env:TASKFLOW_PERF_ACCESS_TOKEN = "replace-with-access-token"
.\tools\performance\collect-runtime.ps1 `
  -AppPid 12345 `
  -Output docs\performance-runtime.json
```

重点记录：

- HikariCP active/idle/pending/max 连接数；
- Spring Executor active/queued 任务数；
- JVM GC pause、堆使用量、JVM flags 和线程快照；
- Redis `keyspace_hits`、`keyspace_misses`、connected clients 和 blocked clients。

某项指标在当前运行模式不可用时，采集脚本会记录 `unavailable`，不能将其当作零值。

## EXPLAIN 与结果记录

在测试数据库执行 [explain.sql](../tools/performance/explain.sql)，保存完整输出，并记录是否使用预期索引、估算行数和是否发生 filesort/temporary。不要只记录优化后的结果。

每次结果至少记录：

| 项目 | 实际值 |
| --- | --- |
| 测试日期 | 2026-08-09 |
| 操作系统 / CPU / 内存 | Windows 11 / 16 CPU；内存未记录 |
| Java 版本和 JVM 参数 | Java 17；JVM 参数未单独记录 |
| MySQL / Redis / RabbitMQ / MinIO 版本 | MySQL 8.4 / Redis 7.4-alpine / RabbitMQ 3.13-management / MinIO RELEASE.2024-12-18T13-15-44Z |
| 用户 / 部门 / 任务数据量 | 100 / 10 / 1000 个预置任务，另有运行期间创建和状态更新请求 |
| 并发用户数 / 持续时间 | 20 / 预热 10 秒 + 采样 60 秒 |
| 各场景 QPS、平均值、p95、p99、错误率 | 已记录在 `docs/performance-baseline.json`，本页同步摘要 |
| 连接池、线程池、GC、Redis 指标 | Actuator 和 Redis 指标已记录在 `docs/performance-runtime.json` |
| EXPLAIN 结论 | 任务列表和通知列表均采用主键倒序扫描并过滤；现有复合索引已存在但本次优化器未选择，需结合真实数据和深分页继续评估 |

阶段 15 的本地基线已完成；后续如调整索引、分页或缓存，必须使用相同数据规模、机器和参数重新对比，不能直接把本轮结果外推到生产。

## 阶段 4 优化结果（2026-08-09）

- `frontend/src/App.tsx` 已对 Dashboard、Login、Management 和 Task 路由使用 `React.lazy`，`npm run build` 通过。旧入口 JS 为 1,156,965 bytes；新构建最大共享 chunk 为 747,991 bytes，入口相关 chunk 下降约 35.38%，但最大 chunk 仍触发 Vite 500 KB 提示，未声称总 JS 或首屏网络传输已经降低。
- 任务列表负责人使用批量 `IN` 查询，通知列表使用单次分页查询，未发现需要立即修复的 N+1。只读 EXPLAIN 显示当前小数据量下任务/通知查询采用主键倒序扫描并过滤；现有复合索引未证明缺失，因此本阶段没有新增 Flyway 索引迁移。
- `docs/performance-runtime-after-optimization.json` 保存了新的 HTTP、连接池、Executor、JVM、RabbitMQ 和 Redis 快照；`docs/performance-explain-after-optimization.txt` 保存了新 EXPLAIN。
- 同参数 20 并发、预热 10 秒、采样 60 秒复测因原管理员凭据不可用而阻塞。员工账号会改变数据范围和写权限，未被用来生成不可比的优化后 QPS/p95/p99。状态和参数见 `docs/performance-baseline-after-optimization.json`，综合说明见 `docs/performance-comparison-2026-08.md`。
