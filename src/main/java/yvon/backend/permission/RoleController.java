package yvon.backend.permission;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import yvon.backend.common.api.ApiResponse;
import yvon.backend.audit.AuditAction;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('role:read')")
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.success(roleService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('role:write') and hasAuthority('data_scope:write')")
    @AuditAction(resourceType = "ROLE", action = "CREATE")
    public ApiResponse<RoleResponse> create(@Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.success(roleService.create(request));
    }

    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('role:write') and hasAuthority('data_scope:write')")
    @AuditAction(resourceType = "ROLE", action = "UPDATE")
    public ApiResponse<RoleResponse> update(@PathVariable Long roleId,
                                             @Valid @RequestBody RoleUpdateRequest request) {
        return ApiResponse.success(roleService.update(roleId, request));
    }
}
