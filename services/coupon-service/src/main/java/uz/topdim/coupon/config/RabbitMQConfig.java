package uz.topdim.coupon.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange("notification.exchange");
    }

    @Bean
    public TopicExchange couponExchange() {
        return new TopicExchange("coupon.exchange");
    }

    @Bean
    public Queue couponRedeemedQueue() {
        return QueueBuilder.durable("coupon.redeemed.queue").build();
    }

    @Bean
    public Binding couponRedeemedBinding(Queue couponRedeemedQueue, TopicExchange couponExchange) {
        return BindingBuilder.bind(couponRedeemedQueue).to(couponExchange).with("coupon.redeemed");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
