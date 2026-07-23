package uz.topdim.payment.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Фильтр аутентификации через заголовки от API Gateway.
 * Читает X-User-Id и X-User-Role, проставленные Gateway после валидации JWT.
 *
 * <p>Строгая валидация:
 * <ul>
 *   <li>X-User-Id должен быть положительным числом</li>
 *   <li>X-User-Role должен быть одним из допустимых значений</li>
 *   <li>Невалидные данные приводят к 401 Unauthorized</li>
 * </ul>
 */
@Slf4j
@Component
public class RoleHeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    /** Допустимые значения ролей — строгий whitelist. */
    private static final Set<String> VALID_ROLES = Set.of(
            "GUEST", "USER", "PARTNER", "MODERATOR", "ADMIN", "SUPER_ADMIN"
    );

    /**
     * Иерархия ролей: каждая роль наследует права предыдущих.
     * SUPER_ADMIN → имеет все роли нижестоящих.
     */
    private static final List<String> ROLE_HIERARCHY = List.of(
            "GUEST", "USER", "PARTNER", "MODERATOR", "ADMIN", "SUPER_ADMIN"
    );

    private static final String HEADER_GATEWAY_AUTH = "X-Gateway-Auth";
    private static final String INTERNAL_PREFIX = "/api/v1/internal/";

    /** Общий секрет gateway↔сервисы. Пусто → энфорс выключен (совместимо со старым поведением). */
    @Value("${internal.auth-secret:}")
    private String gatewaySecret;

    private boolean secretEnabled() {
        return gatewaySecret != null && !gatewaySecret.isBlank();
    }

    /** Постоянное по времени сравнение X-Gateway-Auth с настроенным секретом. */
    private boolean validGatewayAuth(HttpServletRequest request) {
        String provided = request.getHeader(HEADER_GATEWAY_AUTH);
        return provided != null && java.security.MessageDigest.isEqual(
                gatewaySecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                provided.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // #1b: внутренние S2S-эндпоинты (/api/v1/internal/**) при заданном секрете требуют X-Gateway-Auth
        if (secretEnabled() && request.getRequestURI().startsWith(INTERNAL_PREFIX)
                && !validGatewayAuth(request)) {
            log.warn("Доступ к {} без валидного X-Gateway-Auth", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Требуется межсервисная авторизация\"}");
            return;
        }

        String userIdHeader = request.getHeader(HEADER_USER_ID);
        String userRoleHeader = request.getHeader(HEADER_USER_ROLE);

        // Если заголовков нет — пропускаем (анонимный запрос, SecurityConfig решит)
        if (userIdHeader == null || userRoleHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // #1a: анти-спуф — X-User-* принимаем только с валидным X-Gateway-Auth (когда секрет задан).
        // Прямой запрос на порт сервиса с поддельными X-User-* → игнорируем личность (аноним).
        if (secretEnabled() && !validGatewayAuth(request)) {
            log.warn("X-User-* без валидного X-Gateway-Auth — личность проигнорирована (userId header={})", userIdHeader);
            filterChain.doFilter(request, response);
            return;
        }

        // Строгая валидация X-User-Id — только положительные числа
        Long userId;
        try {
            userId = Long.parseLong(userIdHeader);
            if (userId <= 0) {
                log.warn("Невалидный X-User-Id: {} (не положительное число)", userIdHeader);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"Невалидный идентификатор пользователя\"}");
                return;
            }
        } catch (NumberFormatException e) {
            log.warn("Невалидный X-User-Id: {} (не число)", userIdHeader);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Невалидный идентификатор пользователя\"}");
            return;
        }

        // Строгая валидация X-User-Role — только из whitelist
        String role = userRoleHeader.trim().toUpperCase();
        if (!VALID_ROLES.contains(role)) {
            log.warn("Невалидная роль: {} для userId: {}", userRoleHeader, userId);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Невалидная роль пользователя\"}");
            return;
        }

        // Построение authorities с учётом иерархии ролей
        List<SimpleGrantedAuthority> authorities = buildHierarchicalAuthorities(role);

        // Создание Authentication объекта
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Authenticated user {} with role {} (authorities: {})", userId, role, authorities);

        filterChain.doFilter(request, response);
    }

    /**
     * Строит список authorities с учётом иерархии ролей.
     * Если у пользователя роль ADMIN — он также получает USER, PARTNER, MODERATOR.
     */
    private List<SimpleGrantedAuthority> buildHierarchicalAuthorities(String role) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        int roleIndex = ROLE_HIERARCHY.indexOf(role);

        for (int i = 0; i <= roleIndex; i++) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + ROLE_HIERARCHY.get(i)));
        }

        return authorities;
    }
}
