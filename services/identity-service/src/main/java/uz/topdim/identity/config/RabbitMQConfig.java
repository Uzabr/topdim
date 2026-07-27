package uz.topdim.identity.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация RabbitMQ (identity-service).
 * identity — ВТОРОЙ слушатель PaymentCompletedEvent (первый — order-service).
 * Своя durable-очередь {@code payment.completed.identity.queue}, привязанная к тому же
 * TopicExchange {@code payment.exchange} с routing key {@code payment.completed}
 * (НЕ переиспользует очередь order-service — иначе оба сервиса будут воровать сообщения друг у друга;
 * две отдельные очереди на одном exchange+routing key = fan-out, обе получают копию события).
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue paymentCompletedIdentityQueue() {
        return QueueBuilder.durable("payment.completed.identity.queue").build();
    }

    @Bean
    public Binding paymentCompletedIdentityBinding() {
        return BindingBuilder.bind(paymentCompletedIdentityQueue())
                .to(new TopicExchange("payment.exchange"))
                .with("payment.completed");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
