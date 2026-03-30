package uz.topdim.bazaar.config;

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
import uz.topdim.bazaar.security.RoleHeaderAuthenticationFilter;

/**
 * Конфигурация Spring Security для bazaar-service.
 *
 * <p>Правила доступа:
 * <ul>
 *   <li>GET /api/v1/bazaars/**, /api/v1/shops/** — публичный доступ</li>
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

                        // Публичный просмотр базаров и магазинов
                        .requestMatchers(HttpMethod.GET, "/api/v1/bazaars/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/shops/**").permitAll()

                        // Admin endpoints — только ADMIN и SUPER_ADMIN
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // Всё остальное — аутентификация
                        .anyRequest().authenticated()
                )
                .addFilterBefore(roleHeaderAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
