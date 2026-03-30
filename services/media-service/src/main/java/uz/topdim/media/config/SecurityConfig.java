package uz.topdim.media.config;

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
import uz.topdim.media.security.RoleHeaderAuthenticationFilter;

/**
 * Конфигурация Spring Security для media-service.
 *
 * <p>Правила доступа:
 * <ul>
 *   <li>GET /api/v1/media/** — публичный доступ (скачивание файлов)</li>
 *   <li>POST /api/v1/media/upload — аутентифицированные пользователи</li>
 *   <li>DELETE /api/v1/media/** — только ADMIN и SUPER_ADMIN</li>
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

                        // Скачивание файлов — публичный доступ
                        .requestMatchers(HttpMethod.GET, "/api/v1/media/**").permitAll()

                        // Загрузка файлов — только аутентифицированные
                        .requestMatchers(HttpMethod.POST, "/api/v1/media/upload").authenticated()

                        // Удаление файлов — только ADMIN
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/media/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(roleHeaderAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
