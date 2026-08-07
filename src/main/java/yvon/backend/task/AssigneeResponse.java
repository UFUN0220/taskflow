package yvon.backend.task;

import java.time.LocalDateTime;

public record AssigneeResponse(
        Long userId,
        String displayName,
        String assigneeType,
        LocalDateTime acceptedAt
) {
}
