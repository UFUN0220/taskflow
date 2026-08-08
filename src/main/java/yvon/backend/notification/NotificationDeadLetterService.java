package yvon.backend.notification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yvon.backend.common.error.BusinessErrorCode;
import yvon.backend.common.error.BusinessException;
import yvon.backend.organization.PageResponse;
import yvon.backend.reminder.ReminderProperties;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDeadLetterService {

    private final NotificationDeadLetterMapper mapper;
    private final RabbitTemplate rabbitTemplate;
    private final ReminderProperties properties;

    public NotificationDeadLetterService(NotificationDeadLetterMapper mapper, RabbitTemplate rabbitTemplate,
                                         ReminderProperties properties) {
        this.mapper = mapper;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Transactional
    public void recordFailure(String messageId, String eventType, String traceId, String payloadJson,
                              Long planId, Long taskId, int retryCount, String reason, boolean dead) {
        NotificationDeadLetterEntity entity = new NotificationDeadLetterEntity();
        entity.setMessageId(messageId == null || messageId.isBlank() ? "unknown" : messageId);
        entity.setEventType(eventType == null ? "UNKNOWN" : eventType);
        entity.setTraceId(traceId);
        entity.setPayloadJson(payloadJson == null ? "{}" : payloadJson);
        entity.setPlanId(planId);
        entity.setTaskId(taskId);
        entity.setErrorReason(trimReason(reason));
        entity.setRetryCount(retryCount);
        entity.setStatus(dead ? "DEAD" : "RETRYING");
        mapper.recordFailure(entity);
    }

    public PageResponse<NotificationDeadLetterResponse> page(long page, long size, String status) {
        Page<NotificationDeadLetterEntity> result = mapper.selectPage(new Page<>(page, size),
                Wrappers.<NotificationDeadLetterEntity>lambdaQuery()
                        .eq(status != null, NotificationDeadLetterEntity::getStatus, status)
                        .orderByDesc(NotificationDeadLetterEntity::getLastFailedAt));
        return new PageResponse<>(result.getRecords().stream().map(NotificationDeadLetterResponse::from).toList(),
                result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    public void replay(Long deadLetterId) {
        NotificationDeadLetterEntity entity = mapper.selectById(deadLetterId);
        if (entity == null || !"DEAD".equals(entity.getStatus())) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "死信不存在或当前不可补偿");
        }
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType("application/json");
        messageProperties.setMessageId(entity.getMessageId());
        messageProperties.setHeader("x-taskflow-retry-count", 0);
        if (entity.getTraceId() != null) messageProperties.setHeader("traceId", entity.getTraceId());
        Message message = new Message(entity.getPayloadJson().getBytes(StandardCharsets.UTF_8), messageProperties);
        CorrelationData correlation = new CorrelationData(entity.getMessageId() + ":replay:" + deadLetterId);
        rabbitTemplate.send(properties.getRabbit().getExchange(), properties.getRabbit().getRoutingKey(), message,
                correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ publisher confirm rejected: "
                        + (confirm == null ? "unknown" : confirm.getReason()));
            }
        } catch (Exception exception) {
            throw new BusinessException(BusinessErrorCode.BUSINESS_ERROR, "死信重新投递未收到 RabbitMQ 确认");
        }
        if (mapper.markReplayed(deadLetterId) == 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "死信状态已被其他请求改变");
        }
    }

    private String trimReason(String reason) {
        if (reason == null || reason.isBlank()) return "未提供错误原因";
        return reason.length() <= 1000 ? reason : reason.substring(0, 1000);
    }
}
