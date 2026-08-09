# Stage 3 authentication and authorization

## Scope

Stage 3 adds stateless JWT authentication, password verification with BCrypt, current-user context, stable permission authorities, protected user and department read APIs, and environment-controlled development administrator bootstrapping.

## Login flow

1. `POST /api/auth/login` accepts a username or employee number and password.
2. `AuthUserDetailsService` loads only active users and joins active roles and permissions.
3. `DaoAuthenticationProvider` verifies the BCrypt password.
4. `JwtTokenService` issues a signed token containing the user ID and subject.
5. `JwtAuthenticationFilter` validates the token on later requests and reloads the current user, so disabled users and changed permissions do not remain trusted until token expiry.

## Development administrator

The application never stores a plaintext password in Flyway. To create a local administrator, set `TASKFLOW_BOOTSTRAP_ADMIN_ENABLED=true` and `TASKFLOW_BOOTSTRAP_ADMIN_PASSWORD` before starting the backend. The initializer is idempotent and assigns the built-in `system_admin` role.

Do not put a real password in this document or in Flyway. Use a local-only secret manager or an ignored environment file, and rotate the bootstrap password after the first login.

## Permission boundary

V3 seeds stable permission codes such as `auth:me`, `user:read`, and `department:read`. Controllers use backend `@PreAuthorize` checks; hiding a frontend button is not an authorization mechanism. Data-scope filtering and role/permission management APIs remain later work.

## 当前验收边界

当前实现已验证登录、BCrypt 密码校验、JWT 签发、当前用户查询、禁用用户重新加载失效和受保护接口的 401/403 边界。认证仍使用 `SessionCreationPolicy.STATELESS` 的请求模型，但每个 JWT 带有唯一 `jti`，`AuthSessionService` 在 Redis 中保存带 JWT 过期时间的活动会话标记。

`POST /api/auth/logout` 会删除当前 `jti` 的 Redis 会话；HTTP 过滤器和 WebSocket CONNECT 校验都会拒绝已经删除或过期的会话。前端退出会先请求后端，再清理本地 Token；如果后端暂时不可达，页面仍会退出，但会提示服务端未确认，令牌只能等待过期。登录失败按账号和来源地址使用 Redis 执行默认 10 次/60 秒的有限窗口控制。

已补充会话注册、TTL、撤销、登出控制器和限流窗口测试；最新完整 Maven 回归为 64 项执行、0 失败、1 项 Testcontainers 跳过。真实 Compose 登录-登出-旧 Token 失效烟测已通过，Token 仍暂存在 `localStorage`，生产化前应继续评估 HttpOnly Cookie、CSRF 和 CSP 方案。
