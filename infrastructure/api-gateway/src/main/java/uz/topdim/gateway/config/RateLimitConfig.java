package uz.topdim.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Конфигурация Rate Limiting для API Gateway.
 *
 * <p>KeyResolver по IP адресу — для анонимных endpoints (login, register).
 *
 * <p>Используем {@link XForwardedRemoteAddressResolver#maxTrustedIndex(int)} с индексом 1,
 * т.к. за gateway стоит ровно один доверенный прокси (traefik).
 * Это берёт rightmost IP из X-Forwarded-For (тот, что добавил traefik),
 * игнорируя все клиентские подделки слева.
 *
 * <p>При недоступности Redis — fail-open (deny-empty-key: false в application.yml).
 */
@Configuration
public class RateLimitConfig {

    /**
     * Доверяем 1 прокси (traefik). При цепочке XFF: "fake1, fake2, real-ip"
     * maxTrustedIndex(1) возьмёт "real-ip" (добавленный traefik).
     */
    private static final XForwardedRemoteAddressResolver RESOLVER =
            XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    /**
     * KeyResolver по реальному IP адресу клиента.
     * Устойчив к подделке X-Forwarded-For — берёт только доверенный hop.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            InetSocketAddress resolved = RESOLVER.resolve(exchange);
            if (resolved != null) {
                return Mono.just(resolved.getAddress().getHostAddress());
            }

            // Fallback к remote address (если нет XFF вообще)
            if (exchange.getRequest().getRemoteAddress() != null) {
                return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
            }

            return Mono.just("unknown");
        };
    }
}
