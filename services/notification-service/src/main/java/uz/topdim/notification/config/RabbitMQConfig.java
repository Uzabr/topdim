package uz.topdim.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue couponPurchasedQueue() {
        return QueueBuilder.durable("coupon.purchased.queue").build();
    }

    @Bean
    public Binding couponPurchasedBinding() {
        return BindingBuilder.bind(couponPurchasedQueue())
                .to(new TopicExchange("coupon.exchange"))
                .with("coupon.purchased");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
