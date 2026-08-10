# Spring Boot 3.5.16 升级影响记录（Stage 11.5B）

日期：2026-08-11  
范围：仅将 `spring-boot-starter-parent` 从 `3.4.8` 升级到 `3.5.16`；未手工覆盖 Spring Framework、Spring Security、Tomcat、Jackson 或 Netty。

## 为什么进入 Batch A2

- 当前项目父版本是 `3.4.8`。
- 官方 Spring Boot release 页面当前可确认的 3.5 maintenance patch 为 `3.5.16`；本轮没有找到比 `3.4.8` 更高且可复核的 3.4.x 发布版本，因此没有虚构一个 3.4.9+ 版本作为 A1。
- OSV 已确认的高风险条目中，多个修复版本位于 Boot 3.5 管理链，例如 Spring Security 6.5.x、Spring Boot 3.5.x；因此只评估 Boot 3.5，不进入 Boot 4。

## 官方升级影响

Spring Boot 3.5 官方升级说明要求重点检查以下变化：

- `heapdump` Actuator endpoint 默认 access 变为 `NONE`；本项目生产管理面本来只开放健康探针，不依赖 heapdump，因此预期无业务影响。
- profile 命名校验更严格；本项目使用的 `dev`、`test`、`integration`、`acceptance`、`prod` 均符合规则。
- 自动配置执行器的默认 bean 名称从同时提供 `taskExecutor` 和 `applicationTaskExecutor` 收敛为 `applicationTaskExecutor`；本项目需要通过回归测试确认没有按名称注入 `taskExecutor` 的代码。
- TestRestTemplate 的重定向默认行为与常规 RestTemplate 对齐；本项目的验收脚本使用独立 HTTP 客户端，仍由测试回归确认。
- 3.5 移除了 Boot 3.3 已弃用且标记为在 3.5 移除的 API；编译、单测和集成测试是该风险的实际门禁。

## 本项目实际影响与证据

| 检查 | 结果 |
|---|---|
| 业务源码改动 | 仅 parent version；无业务代码改动 |
| Spring Framework/Security/Tomcat/Jackson/Netty | 由 Boot BOM 统一解析，未添加单组件 override |
| Actuator 管理面 | 继续按已有 prod 暴露边界，未新增 heapdump 暴露 |
| Profile | 现有 profile 名称符合 3.5 命名约束 |
| Maven 快速回归 | 远程 integration-security run `31415397055`：84 tests、0 failures、0 errors、0 skipped；BUILD SUCCESS |
| Testcontainers verify | 远程 run `31414454733` / job `93540030517`：84 tests、0 failures、0 errors、0 skipped；Stage12 4/4；JaCoCo 通过 |
| 浏览器 E2E direct/proxy | 待本地 acceptance 环境执行；未用旧证据替代升级后复验 |

## Remote validation

Spring Boot 3.5.16 的 Maven/Testcontainers 兼容性门已通过。实际解析得到 Spring Security 6.5.11、Spring WebMVC 6.2.19、Tomcat 10.1.55、AssertJ 3.27.7；Jackson 2.21.4、Netty 4.1.135.Final、BouncyCastle 1.81 和 Commons Lang3 3.17.0 仍触发 OSV，因此进入单独 Batch B，而不是回滚 Boot 3.5.16。

## 证据边界

本文件记录的是一次小批 BOM 变更的风险分析，不代表升级成功。只有 Maven、Testcontainers、前端、Compose/Kustomize、浏览器 E2E 和远程 CI 均取得本次提交后的真实结果，才能将该批次标记为完成或据此减少漏洞计数。

参考：

- [Spring Boot v3.5.16 release](https://github.com/spring-projects/spring-boot/releases/tag/v3.5.16)
- [Spring Boot 3.5 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.5-Release-Notes)
