package uz.topdim.payment.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация RabbitMQ.
 * Определяет exchanges, queues и bindings для событий.
 * Events: order.created (слушаем), payment.completed (публикуем).
 */
@Configuration
public class RabbitMQConfig {

    // Payment exchange (для публикации PaymentCompletedEvent)
    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange("payment.exchange");
    }

    // Order exchange (для получения OrderCreatedEvent)
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange("order.exchange");
    }

    // Queue для слушания OrderCreatedEvent
    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable("order.created.payment.queue").build();
    }

    // Binding: order.exchange -> order.created -> order.created.payment.queue
    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder.bind(orderCreatedQueue())
                .to(orderExchange())
                .with("order.created");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
