package yvon.backend.organization;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import yvon.backend.common.api.ApiResponse;
import yvon.backend.auth.UserPrincipal;

@Validated
@RestController
@RequestMapping("/api/users")
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class UserController {

    private final UserQueryService userQueryService;
    private final UserManagementService userManagementService;

    public UserController(UserQueryService userQueryService, UserManagementService userManagementService) {
        this.userQueryService = userQueryService;
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public ApiResponse<PageResponse<UserSummaryResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) Long departmentId,
            Authentication authentication) {
        return ApiResponse.success(userQueryService.page(new UserPageQuery(page, size, departmentId),
                (UserPrincipal) authentication.getPrincipal()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:write')")
    public ApiResponse<UserSummaryResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success(userManagementService.create(request));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('user:write')")
    public ApiResponse<UserSummaryResponse> updateStatus(@PathVariable Long userId,
                                                          @Valid @RequestBody UpdateUserStatusRequest request,
                                                          Authentication authentication) {
        return ApiResponse.success(userManagementService.updateStatus(userId, request,
                (UserPrincipal) authentication.getPrincipal()));
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('user:role:write')")
    public ApiResponse<UserSummaryResponse> assignRoles(@PathVariable Long userId,
                                                         @Valid @RequestBody AssignUserRolesRequest request) {
        return ApiResponse.success(userManagementService.assignRoles(userId, request));
    }
}
