# TaskFlow Platform 项目概览

## 项目定位

TaskFlow Platform 是面向企业内部任务协同与流程管理的模块化单体学习项目，服务于本地学习、演示和 Java 后端校招面试。项目没有为了展示技术而拆分微服务。

## 核心能力

- 用户、部门、角色、权限和数据范围；
- 项目与任务、任务状态机、负责人/协作者、评论和附件；
- Redis 提醒索引、RabbitMQ 异步通知、通知查询和 STOMP/WebSocket 实时体验；
- MinIO 文件内容、MySQL 附件元数据、审计和任务操作日志；
- Docker Compose 六服务本地运行、Kind 应用层清单、Playwright 浏览器验收和 Testcontainers 故障验证。

## 技术栈

Java 17、Spring Boot、Spring Security、MyBatis-Plus、MySQL、Redis、RabbitMQ、MinIO、React、TypeScript、Vite、Nginx、Docker Compose、Kind/Kubernetes。

## 当前结论

最终正式评分保持 `83/100`。本地工程、学习、演示和面试范围有条件通过；生产发布不通过。最终 Log4j2 精确治理和远程 CI/OSV 门禁已完成，但生产 TLS、外部密钥轮换、跨实例 WebSocket 广播、多节点 HA 和目标环境容量仍未建立证据。

## 证据边界

Docker Compose 与 Kind 结果是本机/单节点验证，不等于生产 HA；WebSocket 是 best-effort 实时推送，通知事实依赖 MySQL 和 HTTP 补拉；单机性能结果不外推生产容量；OSV 当前快照无命中不等于零供应链风险。
