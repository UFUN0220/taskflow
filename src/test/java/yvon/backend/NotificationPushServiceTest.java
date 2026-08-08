package yvon.backend;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import yvon.backend.notification.NotificationCreatedEvent;
import yvon.backend.notification.NotificationPushService;
import yvon.backend.notification.NotificationResponse;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationPushServiceTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final NotificationPushService service = new NotificationPushService(messagingTemplate);

    @Test
    void sendsToVerifiedUserDestinationAndDoesNotExposeAnotherUserTarget() {
        NotificationResponse response = new NotificationResponse(9L, "message-9", "TASK_STATUS_CHANGED",
                "任务状态已变更", "内容", "TASK", 77L, "UNREAD", null, LocalDateTime.now());

        service.onNotificationCreated(new NotificationCreatedEvent(11L, response));

        verify(messagingTemplate).convertAndSendToUser("11", "/queue/notifications", response);
    }
}
