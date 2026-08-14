# 环境与部署

## 本机工具约定

项目位于 `F:\projects_2027\taskflow-platform\backend`。Maven、Gradle、npm 缓存和 JDK 优先使用 F 盘路径；项目不把 Windows 专用 Maven 参数提交到 CI。Docker Desktop、Kind、kubectl 属于本机工具管理范围，不在项目清理中整体删除。

## Compose

完整 Compose 包含 frontend、backend、MySQL、Redis、RabbitMQ、MinIO 六个服务，容器内部使用 `mysql`、`redis`、`rabbitmq`、`minio` 服务名。停止容器默认保留数据卷；删除卷必须显式确认，验收和故障测试不执行卷删除。

## Acceptance

`scripts/acceptance-up.ps1`、`acceptance-check.ps1`、`acceptance-down.ps1` 使用独立 acceptance profile、独立命名卷和环境变量凭据。测试管理员仅在 acceptance profile 创建或修正，prod 不创建测试账号；密码不进入 Git、README 或日志。完整变量和 CI 用法见 `acceptance-environment.md`。

## Kind

Kind `dev` 是单节点本地集群。Kustomize 管理前端/后端应用层、ConfigMap、Secret、Service、探针和滚动更新；当前不等价于云 LB、跨 AZ、托管数据库、真实证书轮换或生产 HA。`kind-production-like` 的 TLS/WSS 仅是本地开发证书和链路验证。

## 常用命令

```powershell
.\scripts\init-compose.ps1 -Rebuild
.\scripts\acceptance-up.ps1
.\scripts\acceptance-check.ps1
.\scripts\deploy-k8s.ps1 -Overlay kind-production-like
F:\newinstall\kubectl.exe kustomize k8s
```
