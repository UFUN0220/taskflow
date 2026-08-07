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

The development example used during verification was `admin / ChangeMe123!`; do not reuse it outside a local environment.

## Permission boundary

V3 seeds stable permission codes such as `auth:me`, `user:read`, and `department:read`. Controllers use backend `@PreAuthorize` checks; hiding a frontend button is not an authorization mechanism. Data-scope filtering and role/permission management APIs remain later work.
