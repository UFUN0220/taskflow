package yvon.backend.project;

import java.time.LocalDateTime;

public record ProjectResponse(
        Long projectId,
        String projectCode,
        String projectName,
        Long departmentId,
        Long ownerUserId,
        String status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer version
) {
    public static ProjectResponse from(SysProjectEntity entity) {
        return new ProjectResponse(entity.getId(), entity.getProjectCode(), entity.getProjectName(),
                entity.getDepartmentId(), entity.getOwnerUserId(), entity.getStatus(), entity.getStartAt(),
                entity.getEndAt(), entity.getVersion());
    }
}
