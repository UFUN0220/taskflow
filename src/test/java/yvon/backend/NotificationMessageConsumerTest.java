package yvon.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import yvon.backend.notification.NotificationDeadLetterService;
import yvon.backend.notification.NotificationMessageConsumer;
import yvon.backend.notification.NotificationService;
import yvon.backend.reminder.ReminderDueMessage;
import yvon.backend.reminder.ReminderProperties;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationMessageConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationDeadLetterService deadLetterService = mock(NotificationDeadLetterService.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ReminderProperties properties = new ReminderProperties();
    private final NotificationMessageConsumer consumer = new NotificationMessageConsumer(objectMapper,
            notificationService, deadLetterService, rabbitTemplate, properties);

    @Test
    void successfulMessageIsHandledAndManuallyAcked() throws Exception {
        ReminderDueMessage message = new ReminderDueMessage("17", 17L, 7L, "DUE_SOON",
                LocalDateTime.of(2026, 8, 8, 10, 0));
        MessageProperties brokerProperties = new MessageProperties();
        brokerProperties.setDeliveryTag(9L);
        brokerProperties.setMessageId("17");
        Message brokerMessage = new Message(objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8), brokerProperties);
        Channel channel = mock(Channel.class);

        consumer.consume(brokerMessage, channel);

        verify(notificationService).handleReminder(message);
        verify(channel).basicAck(9L, false);
        verifyNoInteractions(deadLetterService, rabbitTemplate);
    }

    @Test
    void failedMessageIsMovedToRetryWithoutRequeue() throws Exception {
        ReminderDueMessage message = new ReminderDueMessage("17", 17L, 7L, "DUE_SOON",
                LocalDateTime.of(2026, 8, 8, 10, 0));
        MessageProperties brokerProperties = new MessageProperties();
        brokerProperties.setDeliveryTag(10L);
        brokerProperties.setMessageId("17");
        Message brokerMessage = new Message(objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8), brokerProperties);
        Channel channel = mock(Channel.class);
        doThrow(new IllegalStateException("database unavailable")).when(notificationService).handleReminder(any());
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3, CorrelationData.class);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        consumer.consume(brokerMessage, channel);

        verify(deadLetterService).recordFailure(eq("17"), eq("REMINDER_DUE"), isNull(), anyString(),
                eq(17L), eq(7L), eq(1), contains("database unavailable"), eq(false));
        verify(rabbitTemplate).send(eq(this.properties.getRabbit().getRetryExchange()),
                eq(this.properties.getRabbit().getRetryRoutingKey()), any(Message.class), any(CorrelationData.class));
        verify(channel).basicAck(10L, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }
}
