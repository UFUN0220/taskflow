package yvon.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import yvon.backend.notification.NotificationDeliveryDiagnostics;
import yvon.backend.notification.NotificationResponse;
import yvon.backend.notification.NotificationWebSocketDiagnosticsInterceptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationWebSocketDiagnosticsInterceptorTest {

    @Test
    void recordsOnlyNotificationCorrelationOnClientOutboundChannel() {
        NotificationDeliveryDiagnostics diagnostics = new NotificationDeliveryDiagnostics();
        NotificationWebSocketDiagnosticsInterceptor interceptor =
                new NotificationWebSocketDiagnosticsInterceptor(diagnostics, new ObjectMapper());
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setDestination("/queue/notifications-user-session-1");
        accessor.setSessionId("session-1");
        Message<?> message = MessageBuilder.createMessage(new NotificationResponse(77L, "task:7:1",
                        "TASK_STATUS_CHANGED", "title", "content", "TASK", 7L, "UNREAD", null,
                        LocalDateTime.now()), accessor.getMessageHeaders());

        interceptor.preSend(message, mock(MessageChannel.class));

        NotificationDeliveryDiagnostics.Checkpoint checkpoint = diagnostics.snapshot(77L).checkpoints().get(0);
        assertThat(checkpoint.checkpoint()).isEqualTo("C3_BROKER_OUTBOUND");
        assertThat(checkpoint.notificationId()).isEqualTo(77L);
        assertThat(checkpoint.destination()).isEqualTo("/queue/notifications-user-session-1");
        assertThat(checkpoint.sessionId()).isEqualTo("session-1");
    }
}
