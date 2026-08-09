# Kind 生产样式本地验证记录（2026-08-09）

## 结论

本记录验证的是 Kind `dev` 单节点上的生产样式应用层配置，不是生产集群验收。

- 集群：Kind `dev`，context `kind-dev`，单节点 `dev-control-plane`。
- 应用：backend 2 副本、frontend 1 副本；增加 namespace 内的 `taskflow-local-edge` Nginx TLS 适配器。
- 通过：Kustomize 静态渲染、生产样式 overlay 应用、backend/frontend/edge rollout、HTTPS 首页、HTTPS `/api/health`、WSS `101 Switching Protocols` 握手。
- 未完成：当前集群没有 `IngressClass/nginx` 对应的 Ingress Controller，因此 `Ingress` 资源只完成静态路由定义，未计为真实 Ingress Controller 运行态证据；STOMP 认证、订阅和浏览器通知闭环未在本阶段重新通过。
- 结论：本地生产样式链路部分通过；不等于生产发布通过。

## 配置分层

```text
k8s/base                         dev 应用层，包含 ConfigMap 和 Deployment/Service
k8s/kustomization.yaml           本地基础清单，包含空值 Secret 模板
k8s/overlays/kind-production-like  Kind 本地生产样式 overlay
  ├─ configmap-patch.yaml        prod profile、health 管理面、taskflow.local Origin
  ├─ ingress.yaml                /、/api、/ws 路由和 taskflow-tls 引用
  └─ edge.yaml                   仅为本地 HTTPS/WSS 验证提供的 namespace 内 Nginx
```

overlay 刻意不包含 `taskflow-secret`，不会覆盖集群中已经注入的 Secret。应用 Secret 通过集群外部注入或预置完成；仓库中的 `k8s/base/secret.yaml` 只包含空值模板，不能作为生产凭据。

本地证书由以下命令生成，私钥位于被 `.gitignore` 忽略的 `runtime-secrets/kind-tls`，有效期短，仅用于 `taskflow.local`：

```powershell
.\scripts\prepare-kind-tls.ps1 -Force
F:\newinstall\kubectl.exe apply -k k8s\overlays\kind-production-like
```

## 路由和代理边界

`taskflow-edge` 的静态路由如下：

| 路径 | 后端 Service | 目的 |
| --- | --- | --- |
| `/` | `frontend:80` | SPA 页面 |
| `/api` | `backend:8080` | REST API |
| `/ws` | `backend:8080` | STOMP/WebSocket |

正式部署应由受控的 Ingress Controller 终止 TLS。由于当前 Kind 集群没有 Ingress Controller，本阶段使用 `taskflow-local-edge` 作为 namespace 内、仅本地的 HTTPS/WSS 验证适配器；它不能代表真实 Ingress Controller、外部负载均衡器或生产证书管理。

应用 prod overlay 使用 `SERVER_FORWARD_HEADERS_STRATEGY=none`。本地 edge 只向后端写入自身计算的 `X-Forwarded-For: $remote_addr` 和 `X-Forwarded-Proto: https`，不透传客户端任意值。后端审计使用连接对端地址，不把任意 `X-Forwarded-For` 当作可信来源；因此客户端伪造该 Header 不应改变审计来源或绕过按来源设计的安全边界。真实生产部署若启用转发 Header，必须把可信代理网络、Header 覆盖行为和 Spring 策略作为同一部署变更验证。

## 可复现命令和实际结果

| 检查 | 命令/方式 | 实际结果 |
| --- | --- | --- |
| Base Kustomize | `F:\newinstall\kubectl.exe kustomize k8s` | 通过 |
| Overlay Kustomize | `F:\newinstall\kubectl.exe kustomize k8s\overlays\kind-production-like` | 通过 |
| Overlay Secret 边界 | 检查渲染结果 | overlay 不包含 `kind: Secret`，不会覆盖 `taskflow-secret` |
| Overlay 应用 | `kubectl apply -k k8s\overlays\kind-production-like` | 通过 |
| Backend rollout | `kubectl rollout status deployment/backend -n taskflow` | 通过，2/2 Ready |
| Frontend rollout | `kubectl rollout status deployment/frontend -n taskflow` | 通过，1/1 Ready |
| Local edge rollout | `kubectl rollout status deployment/taskflow-local-edge -n taskflow` | 通过，1/1 Ready |
| HTTPS 首页 | `https://127.0.0.1:8443/`，Host=`taskflow.local`，跳过本地自签证书校验 | HTTP 200 |
| HTTPS 健康接口 | `https://127.0.0.1:8443/api/health` | HTTP 200，返回 UP |
| WSS 升级 | `wss://taskflow.local/ws/notifications` 等价本地 edge 测试，Host=`taskflow.local` | HTTP 101，WebSocket 升级成功 |
| 证书私钥 | `git check-ignore runtime-secrets/kind-tls/taskflow.local.key` | 被忽略，未进入仓库 |
| XFF 伪造输入 | HTTPS `/api/health` 携带伪造 `X-Forwarded-For` | 请求仍按应用默认策略处理；代码/测试不信任该值 |
| HPA | 未添加 | Kind 当前未具备可复核的 metrics-server/HPA 运行证据 |

一次原始 WebSocket 客户端尝试未完成 STOMP CONNECT；随后使用原始 HTTP/1.1 Upgrade 验证到达后端的协议升级，返回 101。这个结果只证明 WSS 传输升级，不证明 Token 鉴权、SUBSCRIBE、用户通知投递或断线补拉已通过。阶段 3 浏览器 E2E 的真实通知结果仍以 `docs/e2e-browser-report-2026-08-09.md` 的 4/9 记录为准。

backend Pod 的自动恢复曾在阶段 17 验证通过；本阶段 overlay 应用后的 rollout 和健康探针再次通过。若需要重复演练，可删除一个精确匹配 `app.kubernetes.io/name=taskflow-backend` 的 Pod，再等待 Deployment 恢复，禁止删除 namespace、PVC、数据库卷或 Secret。

## Secret/ConfigMap 启动边界

- ConfigMap 仅保存非敏感地址、端口、开关和 profile。
- Secret 保存数据库、RabbitMQ、MinIO、JWT 和 bootstrap admin Secret。
- prod 配置要求显式 Secret，弱默认值或缺失值由应用启动校验拒绝。
- 本记录只检查 Secret 存在和键名，不输出 Secret 值、JWT、密码或 Cookie。
- `taskflow-tls` 是本地临时证书 Secret；真实环境需要外部密钥管理、审计、轮换和恢复演练。

## 明确未覆盖的生产能力

本阶段没有验证或声称以下能力：多节点 HA、云 LB、Ingress Controller 高可用、跨可用区、托管 MySQL/Redis/RabbitMQ/MinIO、真实证书签发与轮换、外部 Secret Manager、WAF、HPA 容量决策、WebSocket 多实例会话迁移、生产级故障切换和 SLA。

