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
import uz.topdim.gateway.service.ReactiveTokenValidationService;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.Set;

/**
 * Фильтр JWT аутентификации в API Gateway.
 * Извлекает токен из Authorization header, валидирует подпись и срок действия,
 * проверяет jti blacklist и securityVersion через Redis,
 * и пробрасывает X-User-Id, X-User-Email, X-User-Role в downstream сервисы.
 *
 * <p>JWT контракт:
 * <ul>
 *   <li>sub = userId (строка)</li>
 *   <li>jti = UUID (для blacklist при logout)</li>
 *   <li>email, role — информационные claims</li>
 *   <li>securityVersion — для массовой инвалидации при block/change-role/change-password</li>
 * </ul>
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ReactiveTokenValidationService tokenValidationService;

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final Set<String> OPEN_AUTH_ENDPOINTS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/guest",
            "/api/v1/auth/password-reset/request",
            "/api/v1/auth/password-reset/confirm",
            "/api/v1/auth/confirm/email"
    );

    private static final List<String> OPEN_ENDPOINTS = List.of(
            "/api/v1/coupons",
            "/api/v1/categories",
            "/api/v1/bazaars",
            "/api/v1/shops",
            "/api/v1/partners/applications",
            "/api/v1/bot",
            "/eureka"
    );

    /** Допустимые значения ролей — строгий whitelist. */
    private static final Set<String> VALID_ROLES = Set.of(
            "GUEST", "USER", "PARTNER", "MODERATOR", "ADMIN", "SUPER_ADMIN"
    );

    /**
     * Роли, которым разрешён доступ к /api/v1/admin/** на уровне gateway.
     * MODERATOR включён, т.к. coupon-service допускает модераторов к /api/v1/admin/coupons/**.
     * Downstream-сервисы (identity, bazaar) дополнительно ограничивают доступ через @PreAuthorize.
     */
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "MODERATOR");

    public JwtAuthenticationFilter(ReactiveTokenValidationService tokenValidationService) {
        this.tokenValidationService = tokenValidationService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Очищаем X-User-* заголовки из внешних запросов (защита от подделки)
        ServerHttpRequest cleanedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Email");
                    headers.remove("X-User-Role");
                    headers.remove("X-Merchant-Id");
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
        Claims claims;
        try {
            claims = validateToken(token);
        } catch (Exception e) {
            log.warn("JWT validation failed for path {}: {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // JWT контракт: sub = userId
        String userId = claims.getSubject();
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);
        String jti = claims.getId();
        Long securityVersion = claims.get("securityVersion", Long.class);

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

        // Реактивная проверка через Redis: jti blacklist + securityVersion
        ServerWebExchange finalExchange = exchange;
        return tokenValidationService.isTokenInvalid(jti, userId, securityVersion)
                .flatMap(invalid -> {
                    if (invalid) {
                        log.warn("Token invalidated via Redis for userId: {}, jti: {}", userId, jti);
                        finalExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return finalExchange.getResponse().setComplete();
                    }

                    // Forward user info to downstream services
                    ServerHttpRequest modifiedRequest = finalExchange.getRequest().mutate()
                            .header("X-User-Id", userId)
                            .header("X-User-Email", email)
                            .header("X-User-Role", role)
                            .build();

                    return chain.filter(finalExchange.mutate().request(modifiedRequest).build());
                });
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

        if (OPEN_AUTH_ENDPOINTS.contains(path)) {
            return true;
        }

        // Специфичное правило для медиа:
        // Скачивание (GET) открыто для всех, Upload (POST) и Delete (DELETE) требуют токен
        if (path.startsWith("/api/v1/media")) {
            return "GET".equalsIgnoreCase(method);
        }

        // Reviews: /api/v1/reviews/coupon/{id} is public (approved reviews),
        // but /api/v1/reviews/coupon/{id}/eligibility and POST /api/v1/reviews require auth
        if (path.startsWith("/api/v1/reviews")) {
            if (path.contains("/eligibility") || "POST".equalsIgnoreCase(method) || path.startsWith("/api/v1/reviews/my")) {
                return false;
            }
            return "GET".equalsIgnoreCase(method) && path.startsWith("/api/v1/reviews/coupon");
        }

        return OPEN_ENDPOINTS.stream().anyMatch(prefix ->
                path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
