package yvon.backend.notification;

public record NotificationCreatedEvent(Long userId, NotificationResponse notification) {
}
