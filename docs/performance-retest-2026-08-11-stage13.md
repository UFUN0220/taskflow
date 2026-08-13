# Stage 13 性能同参数复测记录（2026-08-11）

## 结论

阶段13的 Before/After 性能采样均已按固定契约完成三轮。六个 HTTP 场景在六轮中均为 0 错误，数据集均为 10 部门、100 用户、1000 任务，20 并发，10 秒预热，60 秒采样，随机种子 `20260807`。

但 Before 第1轮明显低于同组第2、3轮，After 三轮也存在单机噪声。由于本阶段只修改了前端懒加载，后端 API、数据库查询、连接池和压测脚本未改变，不能把 After 的平均 QPS/延迟差异归因于前端优化。性能复测状态：`PARTIAL_CAUSALITY`；不是失败，也不是生产容量结论。

## 复测环境

- Git 基线：`239cf8c0f27e96c74c55904a2e8c5ba4405eb4ae`
- Windows 11 + Docker Desktop，本机 acceptance Compose 六服务；每轮使用独立 Compose project 和独立命名卷，结束时只 `stop`，未删除卷。
- HTTP 入口为 backend direct，避免 Nginx 静态资源和 WebSocket 代理延迟混入 API 对比。
- Compose 未设置 CPU/内存配额，记为 `UNBOUNDED_BY_COMPOSE`。
- 原始结果：`evaluation/performance/stage13/pre-run[1-3].json`、`after-run[1-3].json`。
- 运行时采样：对应的 `*-runtime.json`；不可用指标保留为 unavailable，不解释为零。
- 阶段13后置集成复验：Docker Desktop `desktop-linux` context 下显式 `-Dtaskflow.integration=true verify` 真实通过，85 tests、0 failures、0 errors、0 skipped；Stage12 4/4、Stage14 1/1，Flyway V1-V8 成功执行。此前 Docker daemon 未运行导致的跳过仅属于较早一次复验，不是最终状态。

## 参数门禁

| 参数 | Before/After 实际值 |
| --- | --- |
| 部门/用户/任务 | 10 / 100 / 1000 |
| 并发 | 20 |
| 预热/采样 | 10 秒 / 60 秒 |
| 场景 | login、task_list、task_detail、task_create、state_update、notification_list |
| 脚本 | `tools/performance/performance_harness.py` |
| 数据隔离 | 每轮新 acceptance project；避免 `state_update` 消耗下一轮任务 |

## 三轮汇总

| 阶段 | 场景 | 平均 QPS | QPS 范围 | 平均延迟 ms | 平均 p95 ms | 平均 p99 ms | 错误数 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Before | login | 58.300 | 43.000–68.250 | 204.043 | 399.229 | 635.418 | 0 |
| Before | task_list | 55.961 | 41.900–65.117 | 30.848 | 66.031 | 116.147 | 0 |
| Before | task_detail | 57.128 | 41.950–66.917 | 23.581 | 52.168 | 88.298 | 0 |
| Before | task_create | 56.856 | 42.950–66.250 | 68.307 | 117.696 | 194.005 | 0 |
| Before | state_update | 9.178 | 8.767–9.600 | 70.112 | 125.235 | 183.383 | 0 |
| Before | notification_list | 57.289 | 42.650–67.017 | 25.970 | 55.421 | 100.597 | 0 |
| After | login | 64.189 | 62.683–65.417 | 182.181 | 343.162 | 497.996 | 0 |
| After | task_list | 61.322 | 59.867–62.383 | 27.745 | 56.883 | 92.224 | 0 |
| After | task_detail | 63.017 | 61.617–64.150 | 19.477 | 45.092 | 74.313 | 0 |
| After | task_create | 62.067 | 60.433–63.167 | 54.613 | 94.242 | 138.425 | 0 |
| After | state_update | 9.906 | 8.817–10.850 | 60.696 | 99.639 | 130.392 | 0 |
| After | notification_list | 63.044 | 61.450–63.950 | 22.660 | 48.401 | 78.415 | 0 |

## 原始文件索引

- Before fixture：`evaluation/performance/stage13/pre-run[1-3]-fixture.json`
- After fixture：`evaluation/performance/stage13/after-run[1-3]-fixture.json`
- Before runtime：`evaluation/performance/stage13/pre-run[1-3]-runtime.json`
- After runtime：`evaluation/performance/stage13/after-run[1-3]-runtime.json`
- 固定契约：[performance-benchmark-contract-2026-08-11.md](performance-benchmark-contract-2026-08-11.md)

## 证据边界

这些结果证明了当前机器上固定脚本、固定规模和独立数据集可以重复执行且无 HTTP 错误；不证明生产吞吐、容量、SLO、横向扩展或故障时性能。若要得到可归因的后端性能结论，应在控制 CPU/内存、Docker 资源竞争和样本顺序后另设独立实验。

## Final Freeze 依赖审计补充（2026-08-14）

冻结前完整 npm audit 的唯一 high 来自间接开发依赖 `vite@6.4.3 → postcss@8.5.26 → nanoid@3.3.17`（advisory `1139427`，受影响范围 `<3.3.18`）。通过 `frontend/package.json` 的精确 `overrides` 固定 `nanoid@3.3.18`，并用 `npm ci` 验证锁文件。修复后完整审计与生产审计均为 `0 vulnerabilities`；未使用强制升级或关闭门禁。审计 JSON 见 `evaluation/performance/stage13/npm-audit-final-freeze-*.json`。
