package yvon.backend.task;

import java.time.LocalDateTime;

public record TaskPageQuery(
        long page,
        long size,
        String title,
        String status,
        String priority,
        Long assigneeId,
        Long creatorId,
        Long departmentId,
        Long projectId,
        LocalDateTime dueFrom,
        LocalDateTime dueTo,
        LocalDateTime createdFrom,
        LocalDateTime createdTo
) {
}
