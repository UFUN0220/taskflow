# 环境清理报告（2026-08-14）

## 范围与安全边界

本次按“先文档归并、后环境清理”执行。清理目标限于 TaskFlow 项目内可重建的临时产物和缓存；不删除数据库、Docker 卷、凭据、证书私钥、原始验收证据或共享工具安装。

## 文档归并结果

- 新建 `docs/01-overview.md` 至 `docs/09-interview-materials.md` 及 `docs/README.md`；
- 删除已被规范文档吸收的旧重复说明和 stage 4～14 功能说明；
- 保留历史验收报告、失败基线、性能/故障/依赖 JSON、Playwright 证据、详细 Stage 19 面试材料；
- Markdown 相对链接检查：`MARKDOWN_LINKS_OK`；
- `git diff --check`：通过。

## 项目临时内容盘点与处理

| 路径 | 盘点 | 处理 | 原因 |
|---|---:|---|---|
| `frontend/dist` | 1.13 MB | 已删除 | Vite 可重建产物 |
| `frontend/.npm-cache` | 209.52 MB | 已删除 | 项目本地可重建 npm 缓存 |
| `frontend/node_modules` | 150.70 MB | 保留 | 当前本地回归可直接复用 |
| `backend/target` | 130.51 MB | 保留 | 含 JaCoCo、OWASP、Surefire 和当前构建证据 |
| `frontend/playwright-report` | 0.51 MB | 保留 | 浏览器验收报告 |
| `frontend/test-results` | 目录存在 | 保留 | 可能承载失败截图/trace/video |
| `backend/.m2-local` | 150.60 MB | 保留 | 当前 Maven 可复用缓存，且已位于 F 盘 |

删除前对两个目标路径做了项目根目录边界校验；未执行 `docker compose down -v`、卷删除或数据库清理。

## C 盘与共享工具审计

- `C:\Users\Administrator\AppData\Local\Temp` 未发现 TaskFlow/Playwright/Codex 专属临时文件；
- `C:\Users\Administrator\AppData\Local\Codex`、`ms-playwright` 属于 Codex/浏览器共享运行环境，保留；
- `C:\Users\Administrator\AppData\Roaming\Codex`、Docker 配置和 Maven/IDE 共享配置，保留；
- Gradle 全局目录不存在；项目 Maven 用户目录位于 `F:\projects_2027\taskflow-platform\maven-user`，`settings.xml` 的本地仓库也指向 F 盘；
- `F:\newinstall` 工具目录保留，未移动或删除共享工具。

## Docker 现状

只读盘点显示当前 `taskflow-stage13-freeze` acceptance 六服务仍在运行并健康；历史 TaskFlow 容器和命名卷仍存在。为避免删除可复核环境或数据，本次不清理停止容器和任何 Docker 卷。Compose/Kind 的本地边界继续见 `docs/06-environment-and-setup.md`。

## 剩余可选清理

如果后续明确需要回收 Docker 空间，应先逐卷确认用途，再单独审批停止容器和删除卷；这不属于本次安全清理范围。
