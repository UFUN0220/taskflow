package yvon.backend.comment;

import java.time.LocalDateTime;

public record TaskCommentResponse(
        Long commentId,
        Long taskId,
        Long authorUserId,
        String authorDisplayName,
        String commentType,
        String content,
        LocalDateTime createdAt
) {
}
