# 阶段 15：查询优化和压测

## 交付边界

阶段 15 提供可重复的数据准备、HTTP 压测、运行时观测和 EXPLAIN 工具。脚本不会预设或虚构 QPS、延迟、错误率、GC 或 Redis 命中率；这些数值必须在指定机器、指定数据规模和指定配置下实际运行后填写。

本阶段暂不修改业务索引或查询实现。先通过基线结果确认真实瓶颈，再决定是否需要联合索引、批量查询、缓存或深分页优化。

## 工具

- [performance_harness.py](../tools/performance/performance_harness.py)：使用 Python 标准库生成部门、用户、草稿任务，并运行登录、任务列表、任务详情、创建任务、状态更新、通知列表六类场景。
- [explain.sql](../tools/performance/explain.sql)：分析任务列表、任务详情、通知列表的执行计划和现有索引。
- [collect-runtime.ps1](../tools/performance/collect-runtime.ps1)：采集受认证保护的 Actuator 指标、JVM flags/GC/线程和 Redis `INFO stats`。

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
| 测试日期 | 待实际运行填写 |
| 操作系统 / CPU / 内存 | 待实际运行填写 |
| Java 版本和 JVM 参数 | 待实际运行填写 |
| MySQL / Redis / RabbitMQ / MinIO 版本 | 待实际运行填写 |
| 用户 / 部门 / 任务数据量 | 待实际运行填写 |
| 并发用户数 / 持续时间 | 待实际运行填写 |
| 各场景 QPS、平均值、p95、p99、错误率 | 待实际运行填写 |
| 连接池、线程池、GC、Redis 指标 | 待实际运行填写 |
| EXPLAIN 结论 | 待实际运行填写 |

当前工作区仅验证了脚本语法/帮助信息和应用构建，尚未在真实后端、数据库和指定数据规模上执行阶段 15 基线，因此没有可报告的性能指标。
