# 文档整合迁移映射

## Canonical 文档

| 主题 | 新位置 |
|---|---|
| 项目入口 | `docs/01-overview.md` |
| 架构、数据库、状态机、消息 | `docs/02-architecture.md` |
| 阶段演进 | `docs/03-development-history.md` |
| 测试、E2E、故障恢复、CI | `docs/04-testing-and-quality.md` |
| 性能与可观测性 | `docs/05-performance.md` 与 `docs/performance*.json` |
| 环境、Compose、Kind | `docs/06-environment-and-setup.md` 与 `docs/acceptance-environment.md` |
| 安全与依赖 | `docs/07-security-and-dependencies.md` 与 `docs/dependency-security-report.md` |
| 当前验收 | `docs/08-final-acceptance.md` 与 `docs/p1-nginx-stomp-proxy-stability-2026-08-14.md` |
| 面试材料 | `docs/09-interview-materials.md` |

## 历史信息处理

旧阶段报告中的唯一失败、Before/After、命令和边界已摘要到上述 canonical 文档；原始性能 JSON、故障 JSON、依赖 JSON、Playwright trace/screenshot/video 目录和可复现脚本不作为普通 Markdown 清理对象。

## 删除条件

只有在 README、docs 索引和脚本不再引用旧文件，且其唯一信息已迁移后，才删除重复 Markdown。用户要求保留的两个 `evaluation/independent-final-npm-audit-*.json` 不加入 Git，也不删除。

本轮已清理的重复说明文档包括旧的 requirements、architecture、database、deployment、authentication、backend-conventions、testing、learning/interview notes，以及 stage 4～14 的功能阶段说明。历史验收报告、失败基线、原始故障/性能/依赖结果和 Stage 19 详细面试题保留。
