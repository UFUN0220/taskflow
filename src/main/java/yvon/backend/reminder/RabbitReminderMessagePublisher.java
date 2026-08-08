package yvon.backend.reminder;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import yvon.backend.common.trace.TraceIdContext;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "taskflow.reminder.enabled", havingValue = "true")
public class RabbitReminderMessagePublisher implements ReminderMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ReminderProperties properties;

    public RabbitReminderMessagePublisher(RabbitTemplate rabbitTemplate, ReminderProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(ReminderDueMessage message) {
        CorrelationData correlation = new CorrelationData(message.messageId());
        rabbitTemplate.convertAndSend(properties.getRabbit().getExchange(),
                properties.getRabbit().getRoutingKey(), message,
                outbound -> {
                    outbound.getMessageProperties().setMessageId(message.messageId());
                    TraceIdContext.current().ifPresent(traceId ->
                            outbound.getMessageProperties().setHeader("traceId", traceId));
                    return outbound;
                }, correlation);
        try {
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ publisher confirm rejected for reminder " + message.messageId());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for RabbitMQ publisher confirm", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("RabbitMQ reminder publish was not confirmed", exception);
        }
    }
}
