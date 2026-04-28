package uz.topdim.order.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import uz.topdim.order.security.RoleHeaderAuthenticationFilter;

/**
 * Конфигурация Spring Security для order-service.
 * Строгая проверка ролей через заголовки от API Gateway.
 *
 * <p>Правила доступа:
 * <ul>
 *   <li>/api/v1/admin/** — только ADMIN и SUPER_ADMIN</li>
 *   <li>/api/v1/orders/redeem — только PARTNER (погашение купона продавцом)</li>
 *   <li>/api/v1/cart/**, /api/v1/orders/** — авторизованные пользователи</li>
 *   <li>Swagger, Actuator — публичный доступ</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final RoleHeaderAuthenticationFilter roleHeaderAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Служебные endpoints
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()

                        // Internal inter-service calls (Feign: coupon-service → order-service)
                        .requestMatchers("/api/v1/internal/**").permitAll()

                        // Admin endpoints — только ADMIN и SUPER_ADMIN
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // Погашение купона — только PARTNER (и выше по иерархии)
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/redeem").hasRole("PARTNER")

                        // Статистика, история и погашение партнёра — строго только исходная роль PARTNER.
                        // Через RoleHeaderAuthenticationFilter ADMIN наследует ROLE_PARTNER, поэтому hasRole()
                        // здесь небезопасен для cashier redeem сценария.
                        .requestMatchers("/api/v1/partner/**").access((authentication, context) ->
                                new AuthorizationDecision("PARTNER".equalsIgnoreCase(
                                        context.getRequest().getHeader("X-User-Role"))))

                        // Все остальные endpoints — требуют аутентификации
                        .anyRequest().authenticated()
                )
                .addFilterBefore(roleHeaderAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
