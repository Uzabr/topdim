package uz.topdim.order.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Проставляет общий секрет {@code X-Gateway-Auth} во все исходящие Feign-вызовы (S2S),
 * чтобы вызываемый сервис принял запрос к {@code /api/v1/internal/**}. Пусто → не шлём.
 */
@Configuration
public class FeignInternalAuthConfig {

    @Value("${internal.auth-secret:}")
    private String secret;

    @Bean
    public RequestInterceptor internalAuthInterceptor() {
        return template -> {
            if (secret != null && !secret.isBlank()) {
                template.header("X-Gateway-Auth", secret);
            }
        };
    }
}
