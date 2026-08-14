# Stage 13 性能复测契约（2026-08-11）

## 目的与边界

本契约用于比较 Stage 13 前后的本地 HTTP 性能结果。测试结果只代表当前 Windows 11 + Docker Desktop 单机环境，不外推生产容量、可用性或多节点性能。性能复测不修改业务代码和数据库卷；每次复测使用独立的 acceptance Compose 项目和独立命名卷，演练结束只停止容器，不删除卷。

## 固定参数

| 项目 | 固定值 |
| --- | --- |
| 部门 | 10 |
| 用户 | 100 |
| 任务 | 1000 |
| 并发 | 20 |
| 预热 | 10 秒 |
| 采样 | 60 秒 |
| 随机种子 | 20260807 |
| 场景 | login、task_list、task_detail、task_create、state_update、notification_list |
| 入口 | 后端 direct HTTP，避免 Nginx 静态资源和代理延迟污染 API 对比 |
| 脚本 | `tools/performance/performance_harness.py` |
| 数据隔离 | 每个 run 一个全新的 Compose project/命名卷；不复用会被 `state_update` 消耗的任务集 |

## 指标与解释

每个场景记录请求数、成功数、错误数、错误率、QPS、平均延迟、p95、p99 和 HTTP 状态码。性能脚本的百分位数按其当前实现计算，不与其他工具的百分位定义混用。

运行时观测使用 `tools/performance/collect-runtime.ps1` 采集可用的 Actuator 指标和 Redis INFO；采集失败的指标保留 unavailable 记录，不解释为零。Compose 未设置 CPU/内存上限时，报告标注 `UNBOUNDED_BY_COMPOSE`。

## 比较规则

Before 和 After 必须使用相同参数、同一脚本、同一机器条件和相同的独立数据初始化流程。每组至少三轮，报告中同时列出每轮原始 JSON 和三轮均值/范围。由于单机噪声，单次改善不作为生产容量结论；若三轮波动没有收敛，标记为 `PARTIAL`。

## 前端构建契约

记录 `frontend/dist` 每个产物的原始字节数与 gzip 字节数，并区分：

- code splitting：首屏入口初始加载集合变小，但总字节可能基本不变；
- payload reduction：实际代码/依赖字节减少。

保留 Vite 的大 chunk 警告，不通过提高 `chunkSizeWarningLimit` 隐藏问题。前端构建、typecheck、npm audit 和受影响的浏览器回归必须通过后，才记录优化结果。

## 当前冻结基线

- Git commit：`239cf8c0f27e96c74c55904a2e8c5ba4405eb4ae`
- 原 Stage 15 历史参数：与上表一致。
- 原始历史结果位于 `docs/performance-baseline.json`。
- 本地单机、单节点边界仍然有效；本契约不改变生产发布结论。
