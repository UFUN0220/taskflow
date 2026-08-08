package yvon.backend.reminder;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Map;

@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "taskflow.reminder.enabled", havingValue = "true")
public class ReminderMessagingConfiguration {

    @Bean
    RabbitAdmin reminderRabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    TopicExchange reminderExchange(ReminderProperties properties) {
        return new TopicExchange(properties.getRabbit().getExchange(), true, false);
    }

    @Bean
    Queue reminderDispatchQueue(ReminderProperties properties) {
        return new Queue(properties.getRabbit().getQueue(), true);
    }

    @Bean
    Binding reminderDispatchBinding(@Qualifier("reminderDispatchQueue") Queue reminderDispatchQueue,
                                    @Qualifier("reminderExchange") TopicExchange reminderExchange,
                                    ReminderProperties properties) {
        return BindingBuilder.bind(reminderDispatchQueue).to(reminderExchange)
                .with(properties.getRabbit().getRoutingKey());
    }

    @Bean
    Binding taskStatusDispatchBinding(@Qualifier("reminderDispatchQueue") Queue reminderDispatchQueue,
                                      @Qualifier("reminderExchange") TopicExchange reminderExchange,
                                      ReminderProperties properties) {
        return BindingBuilder.bind(reminderDispatchQueue).to(reminderExchange)
                .with(properties.getRabbit().getTaskStatusRoutingKey());
    }

    @Bean
    DirectExchange reminderRetryExchange(ReminderProperties properties) {
        return new DirectExchange(properties.getRabbit().getRetryExchange(), true, false);
    }

    @Bean
    Queue reminderRetryQueue(ReminderProperties properties) {
        return new Queue(properties.getRabbit().getRetryQueue(), true, false, false, Map.of(
                "x-message-ttl", properties.getRabbit().getRetryDelayMs(),
                "x-dead-letter-exchange", properties.getRabbit().getExchange(),
                "x-dead-letter-routing-key", properties.getRabbit().getRoutingKey()));
    }

    @Bean
    Binding reminderRetryBinding(@Qualifier("reminderRetryQueue") Queue reminderRetryQueue,
                                @Qualifier("reminderRetryExchange") DirectExchange reminderRetryExchange,
                                ReminderProperties properties) {
        return BindingBuilder.bind(reminderRetryQueue).to(reminderRetryExchange)
                .with(properties.getRabbit().getRetryRoutingKey());
    }

    @Bean
    DirectExchange reminderDeadLetterExchange(ReminderProperties properties) {
        return new DirectExchange(properties.getRabbit().getDeadLetterExchange(), true, false);
    }

    @Bean
    Queue reminderDeadLetterQueue(ReminderProperties properties) {
        return new Queue(properties.getRabbit().getDeadLetterQueue(), true);
    }

    @Bean
    Binding reminderDeadLetterBinding(@Qualifier("reminderDeadLetterQueue") Queue reminderDeadLetterQueue,
                                     @Qualifier("reminderDeadLetterExchange") DirectExchange reminderDeadLetterExchange,
                                     ReminderProperties properties) {
        return BindingBuilder.bind(reminderDeadLetterQueue).to(reminderDeadLetterExchange)
                .with(properties.getRabbit().getDeadLetterRoutingKey());
    }

    @Bean
    Jackson2JsonMessageConverter reminderMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate reminderRabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter reminderMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(reminderMessageConverter);
        template.setMandatory(true);
        template.setConfirmCallback((correlation, acknowledged, cause) -> {
            if (!acknowledged) {
                org.slf4j.LoggerFactory.getLogger(ReminderMessagingConfiguration.class)
                        .error("RabbitMQ publisher confirm rejected, messageId={}, cause={}",
                                correlation == null ? null : correlation.getId(), cause);
            }
        });
        template.setReturnsCallback(returned ->
                org.slf4j.LoggerFactory.getLogger(ReminderMessagingConfiguration.class)
                        .error("RabbitMQ message was returned, exchange={}, routingKey={}, replyCode={}, replyText={}",
                                returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(),
                                returned.getReplyText()));
        return template;
    }

    @Bean(name = "reminderNotificationListenerContainerFactory")
    SimpleRabbitListenerContainerFactory reminderNotificationListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(10);
        return factory;
    }
}
