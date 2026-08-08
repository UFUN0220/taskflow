# 阶段 17：Kubernetes 本地部署

阶段 17 将前端和后端应用部署到 Kubernetes，把阶段 16 已验证的 MySQL、Redis、RabbitMQ 和 MinIO 继续放在 Docker Compose 中。Kubernetes Pod 通过 `host.docker.internal` 访问 Compose 发布到宿主机的端口。

这是一种本地学习和验证架构，不代表生产架构：中间件仍是单实例，未提供数据库、缓存、消息队列或对象存储的生产级高可用；Kubernetes 部署本身也不等同于接口性能提升。

## 前置条件

- Docker Desktop 正在运行；
- 阶段 16 的 Compose 中间件正在运行：`docker compose up -d mysql redis rabbitmq minio`；
- `kubectl` 已安装并且当前 context 可访问集群；
- 已构建本地镜像：`taskflow-platform-backend:latest` 和 `taskflow-platform-frontend:latest`。

本机当前检测到 `kubectl`，但未检测到 `kind` 或 `minikube`。当前 Kubernetes API context 还要求额外凭据，因此本阶段已完成清单和静态渲染，实际 Pod 自动恢复验证需在可登录的本地集群中执行。

## 配置本地 Secret

`k8s/secret.yaml` 只提供占位值，不能直接作为共享环境配置。应用前请将以下值改为当前 Compose `.env` 中对应的值：

- `DB_USERNAME`、`DB_PASSWORD`；
- `RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD`；
- `MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`；
- `TASKFLOW_JWT_SECRET`；
- 新数据库场景下的 `TASKFLOW_BOOTSTRAP_ADMIN_PASSWORD`。

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

镜像不在公共仓库中，因此需要先将本地镜像导入集群：

```powershell
.\scripts\deploy-k8s.ps1 -Runtime kind
# 或
.\scripts\deploy-k8s.ps1 -Runtime minikube
```

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

## 已知边界

- 本阶段没有部署 MySQL、Redis、RabbitMQ、MinIO 的 Kubernetes StatefulSet；
- 没有加入 Ingress、HPA、Prometheus 或 Grafana；
- 清单中的本地 Secret 是明文占位模板，只适合学习环境，不能提交真实凭据；
- 未在当前机器上宣称 Kubernetes Pod 已运行，因为当前集群 context 尚未通过认证。
