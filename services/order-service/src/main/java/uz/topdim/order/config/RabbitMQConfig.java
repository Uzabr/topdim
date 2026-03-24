package uz.topdim.order.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация RabbitMQ.
 * Определяет exchanges, queues и bindings для событий.
 * Events: order.created, coupon.purchased.
 */
@Configuration
public class RabbitMQConfig {

    // Order exchange
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange("order.exchange");
    }

    // Coupon exchange
    @Bean
    public TopicExchange couponExchange() {
        return new TopicExchange("coupon.exchange");
    }

    // Payment completed queue
    @Bean
    public Queue paymentCompletedQueue() {
        return QueueBuilder.durable("payment.completed.queue").build();
    }

    @Bean
    public Binding paymentCompletedBinding() {
        return BindingBuilder.bind(paymentCompletedQueue())
                .to(new TopicExchange("payment.exchange"))
                .with("payment.completed");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
