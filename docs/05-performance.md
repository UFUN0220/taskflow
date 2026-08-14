# 性能与可观测性

## 测试契约

原阶段性能基线使用 Windows 11、Docker Compose 本机条件、10 个部门、100 个用户、1000 个任务、20 并发、10 秒预热、60 秒采样，覆盖登录、任务列表、详情、创建、状态更新和通知列表。结果只代表当前机器，不外推生产 QPS、容量或 P95/P99。

## 前端 Bundle

通过路由级懒加载降低入口 JS：历史入口约 767 KB，优化后最大共享 chunk 约 583.83 KB。这个结果说明首屏拆分变化，不等于总 JavaScript 传输量下降 23.9%；Vite 500 KB warning 仍保留。

## 后端观测

项目保存了 HTTP 延迟/错误、JVM、Hikari、RabbitMQ、Redis 和 EXPLAIN 快照；Actuator 管理面按 profile 收紧。SQL 优化基于任务/通知列表和权限范围的真实查询与索引检查，不因“有索引”机械增加重复索引。

## 证据索引

- 历史/优化前后 JSON：`performance-baseline*.json`、`performance-runtime*.json`；
- SQL 计划：`performance-explain*.txt`；
- 同参数契约：`performance-benchmark-contract-2026-08-11.md`；
- Stage13 复测原始数据：`evaluation/performance/stage13/`；
- 原始比较说明：`performance-comparison-2026-08.md`、`performance-retest-2026-08-11-stage13.md`。

## 未完成项

未把单机方差、前端拆包与后端吞吐变化混为因果结论；没有目标生产环境容量测试、分布式压测或多节点性能结论。
