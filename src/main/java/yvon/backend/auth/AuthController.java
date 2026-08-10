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
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import yvon.backend.common.api.ApiResponse;
import yvon.backend.audit.AuditAction;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokenService;
    private final AuthSessionService sessionService;
    private final AuthRateLimiter rateLimiter;
    private final SysUserMapper userMapper;
    private final AuthProperties properties;
    private final AuthTokenResolver tokenResolver;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenService tokenService, AuthSessionService sessionService,
                          AuthRateLimiter rateLimiter, SysUserMapper userMapper,
                          AuthProperties properties, AuthTokenResolver tokenResolver) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.rateLimiter = rateLimiter;
        this.userMapper = userMapper;
        this.properties = properties;
        this.tokenResolver = tokenResolver;
    }

    @PostMapping("/login")
    @AuditAction(resourceType = "AUTH", action = "LOGIN")
    public ApiResponse<LoginResponse> login(HttpServletRequest servletRequest,
                                            HttpServletResponse servletResponse,
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
            addAccessCookie(servletResponse, token, tokenService.expiresInSeconds());
            return ApiResponse.success(new LoginResponse(
                    "Bearer", token, tokenService.expiresInSeconds(), principal.userId(),
                    principal.getUsername(), principal.displayName()));
        } catch (AuthenticationException exception) {
            rateLimiter.recordFailure(servletRequest, request.login());
            throw exception;
        }
    }

    @GetMapping("/csrf")
    public ApiResponse<String> csrf(CsrfToken csrfToken) {
        return ApiResponse.success(csrfToken.getToken());
    }

    @PostMapping("/logout")
    @AuditAction(resourceType = "AUTH", action = "LOGOUT")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = tokenResolver.resolve(request);
        if (token != null) sessionService.revoke(token);
        clearAccessCookie(response);
        return ApiResponse.success(null);
    }

    private void addAccessCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        AuthProperties.BrowserCookie config = properties.getBrowserCookie();
        if (!config.isEnabled()) return;
        ResponseCookie cookie = ResponseCookie.from(config.getName(), token)
                .httpOnly(config.isHttpOnly())
                .secure(config.isSecure())
                .sameSite(config.getSameSite())
                .path(config.getPath())
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAccessCookie(HttpServletResponse response) {
        AuthProperties.BrowserCookie config = properties.getBrowserCookie();
        if (!config.isEnabled()) return;
        ResponseCookie cookie = ResponseCookie.from(config.getName(), "")
                .httpOnly(config.isHttpOnly())
                .secure(config.isSecure())
                .sameSite(config.getSameSite())
                .path(config.getPath())
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(Authentication authentication) {
        return ApiResponse.success(CurrentUserResponse.from((UserPrincipal) authentication.getPrincipal()));
    }
}
