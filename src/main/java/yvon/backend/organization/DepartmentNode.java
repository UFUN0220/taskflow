package yvon.backend.organization;

import java.util.List;

public record DepartmentNode(
        Long id,
        Long parentId,
        String departmentCode,
        String departmentName,
        String path,
        Integer level,
        List<DepartmentNode> children
) {
}
