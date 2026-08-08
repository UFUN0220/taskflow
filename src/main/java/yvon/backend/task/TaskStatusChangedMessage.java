package yvon.backend.task;

import java.time.LocalDateTime;

public record TaskStatusChangedMessage(
        String messageId,
        String eventType,
        String traceId,
        Long taskId,
        String fromStatus,
        String toStatus,
        Long operatorId,
        LocalDateTime occurredAt
) {
    public static TaskStatusChangedMessage of(Long taskId, String fromStatus, String toStatus,
                                               Long operatorId, Integer afterVersion, String traceId) {
        return new TaskStatusChangedMessage("task:" + taskId + ":" + afterVersion,
                "TASK_STATUS_CHANGED", traceId, taskId, fromStatus, toStatus, operatorId, LocalDateTime.now());
    }
}
