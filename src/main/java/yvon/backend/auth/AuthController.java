package yvon.backend.auth;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yvon.backend.common.api.ApiResponse;
import yvon.backend.audit.AuditAction;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokenService;
    private final SysUserMapper userMapper;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenService tokenService, SysUserMapper userMapper) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.userMapper = userMapper;
    }

    @PostMapping("/login")
    @AuditAction(resourceType = "AUTH", action = "LOGIN")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.login(), request.password()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        userMapper.updateLastLogin(principal.userId());
        String token = tokenService.issue(principal);
        return ApiResponse.success(new LoginResponse(
                "Bearer", token, tokenService.expiresInSeconds(), principal.userId(),
                principal.getUsername(), principal.displayName()));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(Authentication authentication) {
        return ApiResponse.success(CurrentUserResponse.from((UserPrincipal) authentication.getPrincipal()));
    }
}
