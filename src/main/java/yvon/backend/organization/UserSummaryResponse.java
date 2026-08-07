package yvon.backend.organization;

public record UserSummaryResponse(
        Long userId,
        String username,
        String employeeNo,
        String displayName,
        Long departmentId,
        String status,
        Integer version
) {
    public static UserSummaryResponse from(yvon.backend.auth.SysUserEntity entity) {
        return new UserSummaryResponse(entity.getId(), entity.getUsername(), entity.getEmployeeNo(),
                entity.getDisplayName(), entity.getDepartmentId(), entity.getStatus(), entity.getVersion());
    }
}
