package yvon.backend.permission;

import java.util.List;

public record DataScopeFilter(
        DataScopeType type,
        Long userId,
        Long departmentId,
        List<Long> departmentIds
) {
    public boolean allowsDepartment(Long requestedDepartmentId) {
        return requestedDepartmentId == null || type == DataScopeType.ALL
                || (departmentIds != null && departmentIds.contains(requestedDepartmentId));
    }
}
