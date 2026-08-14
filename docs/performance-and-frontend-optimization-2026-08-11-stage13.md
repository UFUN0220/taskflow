# Stage 13 前端 Bundle 第二轮优化与性能复验

日期：2026-08-11
基线 commit：`239cf8c0f27e96c74c55904a2e8c5ba4405eb4ae`

## 改动

仅将已在 `frontend/src/App.tsx` 中静态导入的 `NotificationCenter` 改为 `lazy(() => import(...))`，并在 Header 中增加局部 `Suspense fallback={null}`。通知 API、WebSocket/STOMP 协议、认证、路由和业务接口均未改变。

选择依据是 NotificationCenter 会引入通知 API、WebSocket 客户端和相关 Ant Design 组件；它不是所有首屏路由都需要的代码。没有引入 manualChunks、没有提高 Vite warning 阈值，也没有升级依赖。

## Bundle 对比

| 指标 | Before | After | 解释 |
| --- | ---: | ---: | --- |
| 首入口 JS raw | 767,334 B | 583,825 B | 下降 23.9%，属于 code splitting |
| 首入口 JS gzip | 248,379 B | 192,272 B | 下降 22.6%，按本地 gzip 采集 |
| JS 总 raw | 1,182,427 B | 1,183,922 B | 基本不变，增加了异步块边界开销 |
| 全部产物 raw（含 CSS） | 未按同一口径记录 | 1,187,995 B | After 机器可读证据已保存 |
| After 全部产物 gzip | 未按同一口径记录 | 388,568 B | After 机器可读证据已保存 |

Before 原始产物：[frontend-bundle-before.json](../evaluation/performance/stage13/frontend-bundle-before.json)；After 原始产物：[frontend-bundle-after.json](../evaluation/performance/stage13/frontend-bundle-after.json)。After Vite 仍报告 `index-mv98YSEC.js` 583,825 B 大 chunk warning；该告警被保留，没有被配置隐藏。

这次收益主要是首屏 code splitting，不是总下载 payload reduction。用户打开通知中心时仍需加载 `NotificationCenter-BsXmqHbM.js`（19,308 B raw / 7,134 B gzip）及其依赖。

## 浏览器回归

使用新前端 acceptance 镜像，Chromium、同一确定性 acceptance 账号和同一 9 场景集合：

| 路径 | 结果 | 说明 |
| --- | --- | --- |
| Backend direct WebSocket | 9/9 PASS | 首次整组 8/9，单独复跑唯一通知派发波动后，再完整整组 9/9 |
| Nginx `/ws` proxy | 9/9 PASS | 真实 CONNECTED、SUBSCRIBE、MESSAGE 和 UI 通知场景均通过 |

最初 19 个测试全失败的证据来自一次错误的测试入口配置：把 Playwright `baseURL` 指向 backend API 端口，`/login` 返回 JSON 401 而不是前端页面。该配置错误已修正为前端端口；它不属于产品回归。修正后保留的真实通知波动为 8/9，单场景复跑和完整复验均通过，不能据此声称三轮浏览器稳定性。

## 性能结论

三轮 Before/After 同参数 HTTP 结果见[性能复测记录](performance-retest-2026-08-11-stage13.md)。六轮均为 0 错误，但单机采样存在明显波动，且前端拆包不会直接优化 backend direct API。故本阶段性能状态为 `PARTIAL_CAUSALITY`；不调整 85/100 评分，不外推生产容量。

## 阶段门禁

- `npm run typecheck`：PASS
- `npm run build`：PASS；Vite 大 chunk warning 保留
- backend direct full E2E：PASS，9/9
- Nginx proxy full E2E：PASS，9/9
- 前后性能采样：PASS，三轮/三轮，错误率 0；因方差未收敛标记 PARTIAL_CAUSALITY
- Maven 默认回归：PASS，85 tests、0 failures、0 errors、5 skipped（未开启 integration 的快速回归）
- 显式 Testcontainers verify：PASS，85 tests、0 failures、0 errors、0 skipped；Stage12 可靠性测试 4/4、Stage14 容器环境测试 1/1，Flyway V1-V8 成功执行，JaCoCo 门禁通过
- npm audit：生产依赖 `--omit=dev` 和完整依赖均为 0 vulnerabilities；最终冻结前的唯一 high 为 Vite 6.4.3 → PostCSS 8.5.26 → nanoid 3.3.17 的间接开发依赖告警，已通过精确 override 升至 nanoid 3.3.18
- Compose/Kustomize 静态校验：PASS；Git/secret 检查仍需在提交前执行

## 评分与后续

总分保持 85/100。当前证据足以允许下一次独立最终复评分，但不构成自动加分：首屏入口变小是明确工程改进，性能后端因果关系仍未证明，Vite 大 chunk、单机资源不受控和生产容量边界仍保留。

## Final Freeze：npm audit High 治理（2026-08-14）

最终冻结前重新执行了官方 npm registry 的完整审计和生产依赖审计。冻结前完整审计为 `1 high / 0 moderate / 0 critical`，生产依赖审计为 `0`。唯一告警为 npm advisory `1139427`：`nanoid@3.3.17` 的受影响范围为 `<3.3.18`，依赖路径是 `vite@6.4.3 → postcss@8.5.26 → nanoid@3.3.17`，且为间接开发依赖。

修复采用 `frontend/package.json` 的精确 `overrides.nanoid = 3.3.18`，并由 `npm ci` 更新锁文件。没有使用 `npm audit fix --force`、没有升级 Vite 主版本、没有关闭审计门禁；PostCSS 当前公开最新版本仍为 8.5.26，故该 override 是兼容且范围最小的治理方式。修复后完整审计和 `--omit=dev` 审计均为 `0 vulnerabilities`。原始 JSON 与修复后 JSON 保存在 `evaluation/performance/stage13/npm-audit-final-freeze-*.json`。

最终冻结浏览器回归：direct 9/9、Nginx proxy 9/9；一次整组通知派发波动保留了失败证据，单测复跑和完整复验均通过，不据此声称三轮稳定性。评分仍保持 85/100，阶段状态仍为 `COMPLETED_WITH_PERFORMANCE_CAUSALITY_LIMIT`。
