package uz.topdim.coupon.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import uz.topdim.coupon.security.RoleHeaderAuthenticationFilter;

/**
 * Конфигурация Spring Security для coupon-service.
 *
 * <p>Правила доступа:
 * <ul>
 *   <li>GET /api/v1/coupons/**, /api/v1/categories/** — публичный доступ (каталог)</li>
 *   <li>/api/v1/admin/** — только ADMIN и SUPER_ADMIN</li>
 *   <li>Остальное — аутентификация обязательна</li>
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
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()

                        // Публичный каталог купонов (только чтение)
                        .requestMatchers(HttpMethod.GET, "/api/v1/coupons/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/merchants/**").permitAll()

                        // Admin/Moderator endpoints (модаратору теперь тоже можно работать с контентом)
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN", "MODERATOR")

                        // Partner endpoints — PARTNER, ADMIN, SUPER_ADMIN
                        .requestMatchers("/api/v1/partner/**").hasAnyRole("PARTNER", "ADMIN", "SUPER_ADMIN")

                        // Всё остальное — аутентификация
                        .anyRequest().authenticated()
                )
                .addFilterBefore(roleHeaderAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
