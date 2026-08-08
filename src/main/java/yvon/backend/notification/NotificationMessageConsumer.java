package yvon.backend.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import yvon.backend.reminder.ReminderDueMessage;
import yvon.backend.reminder.ReminderProperties;
import yvon.backend.task.TaskStatusChangedMessage;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "taskflow.reminder.enabled", havingValue = "true")
public class NotificationMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationMessageConsumer.class);
    private static final String RETRY_HEADER = "x-taskflow-retry-count";
    private static final String TRACE_HEADER = "traceId";

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final NotificationDeadLetterService deadLetterService;
    private final RabbitTemplate rabbitTemplate;
    private final ReminderProperties properties;

    public NotificationMessageConsumer(ObjectMapper objectMapper, NotificationService notificationService,
                                       NotificationDeadLetterService deadLetterService, RabbitTemplate rabbitTemplate,
                                       ReminderProperties properties) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.deadLetterService = deadLetterService;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @RabbitListener(queues = "${taskflow.reminder.rabbit.queue}",
            containerFactory = "reminderNotificationListenerContainerFactory")
    public void consume(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        JsonNode root = null;
        String messageId = message.getMessageProperties().getMessageId();
        String eventType = "UNKNOWN";
        String traceId = header(message.getMessageProperties(), TRACE_HEADER);
        try {
            root = objectMapper.readTree(payload);
            eventType = root.path("eventType").asText("UNKNOWN");
            if (messageId == null || messageId.isBlank()) messageId = root.path("messageId").asText(null);
            if (traceId == null) traceId = root.path("traceId").asText(null);
            MDC.put("messageId", messageId == null ? "unknown" : messageId);
            if (traceId != null) MDC.put("traceId", traceId);
            if ("REMINDER_DUE".equals(eventType)) {
                notificationService.handleReminder(objectMapper.treeToValue(root, ReminderDueMessage.class));
            } else if ("TASK_STATUS_CHANGED".equals(eventType)) {
                notificationService.handleTaskStatus(objectMapper.treeToValue(root, TaskStatusChangedMessage.class));
            } else {
                throw new IllegalArgumentException("不支持的事件类型: " + eventType);
            }
            log.debug("Notification message acknowledged, messageId={}, eventType={}", messageId, eventType);
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            handleFailure(message, channel, deliveryTag, payload, root, messageId, eventType, traceId, exception);
        } finally {
            MDC.remove("messageId");
            MDC.remove("traceId");
        }
    }

    private void handleFailure(Message message, Channel channel, long deliveryTag, String payload,
                               JsonNode root, String messageId, String eventType, String traceId,
                               Exception exception) throws Exception {
        int retryCount = retryCount(message.getMessageProperties());
        int nextAttempt = retryCount + 1;
        boolean dead = nextAttempt >= properties.getRabbit().getMaxAttempts();
        Long planId = root == null ? null : longValue(root, "planId");
        Long taskId = root == null ? null : longValue(root, "taskId");
        String reason = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        try {
            deadLetterService.recordFailure(messageId, eventType, traceId, payload, planId, taskId,
                    nextAttempt, reason, dead);
            if (dead) {
                publishToDeadLetter(message, messageId, traceId);
            } else {
                publishToRetry(message, messageId, traceId, nextAttempt);
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception publishException) {
            log.error("Notification message failure handling failed, messageId={}, eventType={}, attempt={}",
                    messageId, eventType, nextAttempt, publishException);
            channel.basicReject(deliveryTag, false);
        }
    }

    private void publishToRetry(Message source, String messageId, String traceId, int retryCount) {
        CorrelationData correlation = new CorrelationData(messageId + ":retry:" + retryCount);
        rabbitTemplate.send(properties.getRabbit().getRetryExchange(), properties.getRabbit().getRetryRoutingKey(),
                copyMessage(source, messageId, traceId, retryCount), correlation);
        awaitConfirm(correlation, "重试消息");
    }

    private void publishToDeadLetter(Message source, String messageId, String traceId) {
        CorrelationData correlation = new CorrelationData(messageId + ":dead");
        rabbitTemplate.send(properties.getRabbit().getDeadLetterExchange(),
                properties.getRabbit().getDeadLetterRoutingKey(), copyMessage(source, messageId, traceId, null), correlation);
        awaitConfirm(correlation, "死信消息");
    }

    private void awaitConfirm(CorrelationData correlation, String label) {
        try {
            var confirm = correlation.getFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ " + label + "未确认");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 RabbitMQ " + label + "确认时被中断", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("RabbitMQ " + label + "投递失败", exception);
        }
    }

    private Message copyMessage(Message source, String messageId, String traceId, Integer retryCount) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(source.getMessageProperties().getContentType());
        properties.setMessageId(messageId);
        if (traceId != null) properties.setHeader(TRACE_HEADER, traceId);
        if (retryCount != null) properties.setHeader(RETRY_HEADER, retryCount);
        return new Message(source.getBody(), properties);
    }

    private int retryCount(MessageProperties properties) {
        Object value = properties.getHeaders().get(RETRY_HEADER);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try { return Integer.parseInt(text); } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String header(MessageProperties properties, String name) {
        Object value = properties.getHeaders().get(name);
        return value == null ? null : value.toString();
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }
}
