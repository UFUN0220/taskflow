package yvon.backend.notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        String sourceMessageId,
        String notificationType,
        String title,
        String content,
        String aggregateType,
        Long aggregateId,
        String status,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
    static NotificationResponse from(NotificationEntity entity) {
        return new NotificationResponse(entity.getId(), entity.getSourceMessageId(), entity.getNotificationType(),
                entity.getTitle(), entity.getContent(), entity.getAggregateType(), entity.getAggregateId(),
                entity.getStatus(), entity.getReadAt(), entity.getCreatedAt());
    }
}
