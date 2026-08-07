package yvon.backend.reminder;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
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
    Binding reminderDispatchBinding(Queue reminderDispatchQueue, TopicExchange reminderExchange,
                                    ReminderProperties properties) {
        return BindingBuilder.bind(reminderDispatchQueue).to(reminderExchange)
                .with(properties.getRabbit().getRoutingKey());
    }

    @Bean
    Jackson2JsonMessageConverter reminderMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
