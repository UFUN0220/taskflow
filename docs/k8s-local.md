# 阶段 17：Kubernetes 本地部署

阶段 17 将前端和后端应用部署到 Kubernetes，把阶段 16 已验证的 MySQL、Redis、RabbitMQ 和 MinIO 继续放在 Docker Compose 中。Kubernetes Pod 通过 `host.docker.internal` 访问 Compose 发布到宿主机的端口。

这是一种本地学习和验证架构，不代表生产架构：中间件仍是单实例，未提供数据库、缓存、消息队列或对象存储的生产级高可用；Kubernetes 部署本身也不等同于接口性能提升。

## 前置条件

- Docker Desktop 正在运行；
- 阶段 16 的 Compose 中间件正在运行：`docker compose up -d mysql redis rabbitmq minio`；
- `kubectl` 已安装并且当前 context 可访问集群；
- 已构建本地镜像：`taskflow-platform-backend:latest` 和 `taskflow-platform-frontend:latest`。

### 当前本地 Kind 环境

| 项目 | 信息 |
| --- | --- |
| 工具 | Kind（Kubernetes in Docker） |
| 集群名称 | `dev` |
| Kubernetes 版本 | `v1.32.2` |
| 节点 | 单节点控制平面 `dev-control-plane` |
| Context | `kind-dev` |
| 状态 | `Ready` |
| `kubectl` 当前 PATH 命令 | `v1.36.1`，`C:\Program Files\Docker\Docker\resources\bin\kubectl.exe` |
| `kubectl` F 盘副本 | `v1.32.0`，`F:\newinstall\kubectl.exe` |
| `kind` | `v0.27.0`，`F:\newinstall\kind.exe` |
| Kustomize | `v5.8.1`，内置于 kubectl |
| 工具目录 | `F:\newinstall` 已加入永久系统 Path；当前 PATH 优先解析 Docker Desktop 自带 kubectl |

本机已在 `kind-dev` 完成阶段 17 应用层实机验收：后端 2 个副本、前端 1 个副本均通过 Deployment 探针；前后端端口转发健康检查均返回 HTTP 200；删除一个后端 Pod 后 Deployment 自动恢复；前后端滚动重启均成功。验证使用的 Secret 来自当前本地 Compose 运行环境，未写入仓库中的明文模板。

## 配置本地 Secret

`k8s/secret.yaml` 现在只提供空值字段，不能直接作为共享环境配置，也不应把真实值回填提交。应用前请通过本地未提交文件、`kubectl create secret`、Secret Manager 或其他外部注入方式提供以下值：

- `DB_USERNAME`、`DB_PASSWORD`；
- `RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD`；
- `MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`；
- `TASKFLOW_JWT_SECRET`；
- 新数据库场景下的 `TASKFLOW_BOOTSTRAP_ADMIN_PASSWORD`。

当前 ConfigMap 明确使用 `SPRING_PROFILES_ACTIVE=dev`，仅适合本地学习。生产应使用单独 overlay/部署平台 Secret，并设置 `SPRING_PROFILES_ACTIVE=prod`；prod 会拒绝缺失或弱 Secret。不要直接 apply 仓库中的空 Secret 模板，也不要把本地 Compose 密码写入 Git。

如果连接的是已经初始化过的数据库，bootstrap admin 密码不会覆盖已有 `admin` 用户；登录密码以数据库中当前账号记录为准。

## Docker Desktop Kubernetes

启用 Docker Desktop 的 Kubernetes 后，确认 `kubectl config current-context` 指向该集群。先渲染清单：

```powershell
.\scripts\deploy-k8s.ps1
```

确认 Secret 后应用：

```powershell
.\scripts\deploy-k8s.ps1 -Apply
kubectl get pods,svc -n taskflow
kubectl rollout status deployment/backend -n taskflow
kubectl rollout status deployment/frontend -n taskflow
```

通过端口转发访问前端：

```powershell
kubectl port-forward -n taskflow svc/frontend 5173:80
```

然后访问 <http://localhost:5173>。前端 Nginx 通过 Kubernetes Service 名 `backend` 代理 `/api`、`/actuator` 和 `/ws`。

## Kind 或 Minikube

当前项目使用 Kind 集群时先切换 context：

```powershell
kubectl config use-context kind-dev
kind get clusters
kubectl get nodes
```

镜像不在公共仓库中，因此需要先将本地镜像导入集群：

```powershell
.\scripts\deploy-k8s.ps1 -Runtime kind
# 或
.\scripts\deploy-k8s.ps1 -Runtime minikube
```

当前 Kind 集群名称为 `dev`；如使用其他集群名称，可传入 `-KindClusterName <name>`。脚本会将本地前后端镜像加载到指定 Kind 集群。脚本已支持 PATH 中没有 `kind`/`kubectl` 时回退到 `F:\newinstall` 下的工具。

脚本会在应用前导入两个本地镜像。若 Minikube 环境不能解析 `host.docker.internal`，请将 `k8s/configmap.yaml` 的 `INFRA_HOST` 改为集群可访问宿主机的地址（常见值为 `host.minikube.internal`），并确保 Compose 端口对该地址可达。

## 验证滚动更新和自动恢复

```powershell
kubectl get deployment -n taskflow
kubectl rollout history deployment/backend -n taskflow
kubectl delete pod -n taskflow -l app.kubernetes.io/name=taskflow-backend --wait=false
kubectl get pods -n taskflow -w
kubectl rollout status deployment/backend -n taskflow --timeout=180s
```

删除 Pod 只删除 Kubernetes 中的临时 Pod，不删除数据库、Docker 卷或业务数据。验证期间应观察：

1. Deployment 保持两个后端副本目标数；
2. 新 Pod 在 `/api/health` 通过 startup/readiness probe 前不会加入 Service Endpoints；
3. 旧 Pod 退出后，Deployment 自动创建替代 Pod；
4. `kubectl get endpoints -n taskflow backend` 只列出已就绪的 Pod 地址。

本次实机结果：`backend` Deployment 为 `2/2`，`frontend` Deployment 为 `1/1`；前端 `http://127.0.0.1:15173/` 和后端 `/api/health` 端口转发均返回 HTTP 200；后端 Pod 删除后已恢复，随后前后端 `rollout restart` 均成功。

## 已知边界

- 本阶段没有部署 MySQL、Redis、RabbitMQ、MinIO 的 Kubernetes StatefulSet；
- 没有加入 Ingress、HPA、Prometheus 或 Grafana；
- 清单中的 Secret 是空值模板，只适合生成本地配置结构，不能直接 apply 共享环境，也不能提交真实凭据；
- 本次只验证了 Kind 单节点上的应用层部署、探针、Pod 恢复和滚动重启；不代表多节点生产高可用、Ingress/TLS、HPA、故障域隔离或中间件高可用。
