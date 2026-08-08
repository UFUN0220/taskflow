package yvon.backend.notification;

import java.time.LocalDateTime;

public record NotificationDeadLetterResponse(
        Long deadLetterId,
        String messageId,
        String eventType,
        String traceId,
        String payloadJson,
        Long planId,
        Long taskId,
        String errorReason,
        Integer retryCount,
        String status,
        LocalDateTime lastFailedAt,
        LocalDateTime replayedAt
) {
    static NotificationDeadLetterResponse from(NotificationDeadLetterEntity entity) {
        return new NotificationDeadLetterResponse(entity.getId(), entity.getMessageId(), entity.getEventType(),
                entity.getTraceId(), entity.getPayloadJson(), entity.getPlanId(), entity.getTaskId(),
                entity.getErrorReason(), entity.getRetryCount(), entity.getStatus(), entity.getLastFailedAt(),
                entity.getReplayedAt());
    }
}
