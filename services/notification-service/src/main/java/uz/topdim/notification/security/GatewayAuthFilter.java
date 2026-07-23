package uz.topdim.notification.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Notification-service не использует Spring Security — читает {@code X-User-Id} из заголовка
 * напрямую. Этот фильтр гарантирует, что {@code X-User-Id} принимается только от gateway
 * (по совпадению общего секрета {@code X-Gateway-Auth}), закрывая header-spoofing при прямом
 * доступе к порту сервиса. Секрет пуст → энфорс выключен (обратная совместимость).
 */
@Slf4j
@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_GATEWAY_AUTH = "X-Gateway-Auth";

    @Value("${internal.auth-secret:}")
    private String gatewaySecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean enabled = gatewaySecret != null && !gatewaySecret.isBlank();
        if (enabled && request.getHeader(HEADER_USER_ID) != null && !validGatewayAuth(request)) {
            log.warn("X-User-Id без валидного X-Gateway-Auth на {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Требуется авторизация через gateway\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean validGatewayAuth(HttpServletRequest request) {
        String provided = request.getHeader(HEADER_GATEWAY_AUTH);
        return provided != null && MessageDigest.isEqual(
                gatewaySecret.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
