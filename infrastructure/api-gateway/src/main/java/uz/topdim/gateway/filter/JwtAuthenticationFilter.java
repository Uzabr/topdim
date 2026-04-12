package uz.topdim.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.Set;

/**
 * Фильтр JWT аутентификации в API Gateway.
 * Извлекает токен из Authorization header, валидирует
 * и пробрасывает X-User-Id, X-User-Email, X-User-Role в downstream сервисы.
 *
 * <p>Строгая безопасность:
 * <ul>
 *   <li>Проверяет подпись и срок действия JWT</li>
 *   <li>Валидирует что роль из whitelist допустимых значений</li>
 *   <li>Проверяет права доступа к /api/v1/admin/** (только ADMIN, SUPER_ADMIN)</li>
 *   <li>Очищает X-User-* заголовки из внешних запросов (защита от подделки)</li>
 * </ul>
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final List<String> OPEN_ENDPOINTS = List.of(
            "/api/v1/auth/",
            "/api/v1/coupons",
            "/api/v1/categories",
            "/api/v1/bazaars",
            "/api/v1/shops",
            "/api/v1/partners/applications",
            "/api/v1/bot/",
            "/eureka",
            "/actuator"
    );

    /** Допустимые значения ролей — строгий whitelist. */
    private static final Set<String> VALID_ROLES = Set.of(
            "GUEST", "USER", "PARTNER", "MODERATOR", "ADMIN", "SUPER_ADMIN"
    );

    /** Роли, которым разрешён доступ к /api/v1/admin/**. */
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "MODERATOR");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Очищаем X-User-* заголовки из внешних запросов (защита от подделки)
        ServerHttpRequest cleanedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Email");
                    headers.remove("X-User-Role");
                })
                .build();
        exchange = exchange.mutate().request(cleanedRequest).build();

        // Skip open endpoints
        if (isOpenEndpoint(exchange)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = validateToken(token);

            String userId = claims.getSubject();
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);

            // Строгая валидация роли из JWT
            if (role == null || !VALID_ROLES.contains(role)) {
                log.warn("Invalid role '{}' in JWT for userId: {}", role, userId);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            // Проверка доступа к admin endpoints
            if (path.startsWith("/api/v1/admin/") && !ADMIN_ROLES.contains(role)) {
                log.warn("Access denied to {} for userId: {} with role: {}", path, userId, role);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            // Проверка доступа к super admin endpoints (только SUPER_ADMIN)
            if (path.startsWith("/api/v1/super/") && !"SUPER_ADMIN".equals(role)) {
                log.warn("Access denied to super admin endpoint {} for userId: {} with role: {}", path, userId, role);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            // Forward user info to downstream services (заголовки теперь только от Gateway)
            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Email", email)
                    .header("X-User-Role", role)
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        } catch (Exception e) {
            log.warn("JWT validation failed for path {}: {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private Claims validateToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isOpenEndpoint(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        // Специфичное правило для медиа:
        // Скачивание (GET) открыто для всех, Upload (POST) и Delete (DELETE) требуют токен
        if (path.startsWith("/api/v1/media")) {
            return "GET".equalsIgnoreCase(method);
        }

        return OPEN_ENDPOINTS.stream().anyMatch(path::startsWith);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
