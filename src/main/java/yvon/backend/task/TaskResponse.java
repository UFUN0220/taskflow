package yvon.backend.task;

import java.time.LocalDateTime;
import java.util.List;

public record TaskResponse(
        Long taskId,
        String taskNo,
        String title,
        String description,
        Long projectId,
        Long departmentId,
        Long creatorId,
        String status,
        String priority,
        LocalDateTime dueAt,
        Integer version,
        List<AssigneeResponse> assignees
) {
    public static TaskResponse from(TaskEntity entity, List<AssigneeResponse> assignees) {
        return new TaskResponse(entity.getId(), entity.getTaskNo(), entity.getTitle(), entity.getDescription(),
                entity.getProjectId(), entity.getDepartmentId(), entity.getCreatorId(), entity.getStatus(),
                entity.getPriority(), entity.getDueAt(), entity.getVersion(), assignees);
    }
}
