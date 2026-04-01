package uz.topdim.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Глобальный фильтр для добавления Security Headers к каждому ответу.
 * Дополняет Spring Security headers (CSP, HSTS — которые Security framework не добавляет по умолчанию).
 *
 * <p>Добавляет:
 * <ul>
 *   <li>Content-Security-Policy (report-only на старте — безопасно для React)</li>
 *   <li>Permissions-Policy (ограничивает API браузера)</li>
 * </ul>
 *
 * <p>HSTS добавляется только при HTTPS (определяется по X-Forwarded-Proto или scheme).
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();

            // CSP: report-only на начальном этапе (безопасно для React с inline styles)
            // Когда фронт будет готов — перевести на enforce
            headers.addIfAbsent(
                    "Content-Security-Policy-Report-Only",
                    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                            + "img-src 'self' data: blob:; font-src 'self'; connect-src 'self'; "
                            + "frame-ancestors 'none'"
            );

            // Permissions-Policy: ограничиваем API браузера
            headers.addIfAbsent(
                    "Permissions-Policy",
                    "camera=(), microphone=(), geolocation=(self), payment=(self)"
            );

            // HSTS: только при HTTPS
            String proto = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto");
            boolean isHttps = "https".equalsIgnoreCase(proto)
                    || "https".equals(exchange.getRequest().getURI().getScheme());
            if (isHttps) {
                headers.addIfAbsent(
                        "Strict-Transport-Security",
                        "max-age=31536000; includeSubDomains"
                );
            }
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
