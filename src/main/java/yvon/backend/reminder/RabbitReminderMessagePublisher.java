package yvon.backend.reminder;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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
        rabbitTemplate.convertAndSend(properties.getRabbit().getExchange(),
                properties.getRabbit().getRoutingKey(), message,
                outbound -> {
                    outbound.getMessageProperties().setMessageId(message.messageId());
                    return outbound;
                });
    }
}
