package uz.topdim.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Конфигурация Rate Limiting для API Gateway.
 *
 * <p>KeyResolver по IP адресу — для анонимных endpoints (login, register).
 * Учитывает X-Forwarded-For для работы за reverse proxy (Nginx).
 *
 * <p>При недоступности Redis — fail-open (deny-empty-key: false в application.yml).
 */
@Configuration
public class RateLimitConfig {

    /**
     * KeyResolver по IP адресу.
     * Приоритет: X-Forwarded-For → X-Real-IP → Remote Address.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                // Берём первый IP из цепочки (оригинальный клиент)
                String clientIp = forwardedFor.split(",")[0].trim();
                return Mono.just(clientIp);
            }

            String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return Mono.just(realIp.trim());
            }

            // Fallback к remote address
            if (exchange.getRequest().getRemoteAddress() != null) {
                return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
            }

            return Mono.just("unknown");
        };
    }
}
