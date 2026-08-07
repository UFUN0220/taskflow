package yvon.backend.permission;

import java.util.List;

public record RoleResponse(
        Long roleId,
        String roleCode,
        String roleName,
        String status,
        Boolean builtIn,
        Integer version,
        List<String> permissionCodes,
        DataScopeType scopeType
) {
}
