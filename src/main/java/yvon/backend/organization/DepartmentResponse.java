package yvon.backend.organization;

public record DepartmentResponse(
        Long departmentId,
        Long parentId,
        String departmentCode,
        String departmentName,
        String path,
        Integer level,
        String status,
        Integer version
) {
    public static DepartmentResponse from(SysDepartmentEntity entity) {
        return new DepartmentResponse(entity.getId(), entity.getParentId(), entity.getDepartmentCode(),
                entity.getDepartmentName(), entity.getPath(), entity.getLevel(), entity.getStatus(), entity.getVersion());
    }
}
