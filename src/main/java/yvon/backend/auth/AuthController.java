package yvon.backend.auth;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import yvon.backend.common.api.ApiResponse;
import yvon.backend.audit.AuditAction;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokenService;
    private final AuthSessionService sessionService;
    private final AuthRateLimiter rateLimiter;
    private final SysUserMapper userMapper;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenService tokenService, AuthSessionService sessionService,
                          AuthRateLimiter rateLimiter, SysUserMapper userMapper) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.rateLimiter = rateLimiter;
        this.userMapper = userMapper;
    }

    @PostMapping("/login")
    @AuditAction(resourceType = "AUTH", action = "LOGIN")
    public ApiResponse<LoginResponse> login(HttpServletRequest servletRequest,
                                            @Valid @RequestBody LoginRequest request) {
        rateLimiter.checkAllowed(servletRequest, request.login());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.login(), request.password()));
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            rateLimiter.recordSuccess(servletRequest, request.login());
            userMapper.updateLastLogin(principal.userId());
            String token = tokenService.issue(principal);
            sessionService.register(token);
            return ApiResponse.success(new LoginResponse(
                    "Bearer", token, tokenService.expiresInSeconds(), principal.userId(),
                    principal.getUsername(), principal.displayName()));
        } catch (AuthenticationException exception) {
            rateLimiter.recordFailure(servletRequest, request.login());
            throw exception;
        }
    }

    @PostMapping("/logout")
    @AuditAction(resourceType = "AUTH", action = "LOGOUT")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            sessionService.revoke(authorization.substring("Bearer ".length()).trim());
        }
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(Authentication authentication) {
        return ApiResponse.success(CurrentUserResponse.from((UserPrincipal) authentication.getPrincipal()));
    }
}
