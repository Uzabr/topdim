package uz.topdim.identity.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import uz.topdim.identity.security.CustomUserDetailsService;
import uz.topdim.identity.security.RoleHeaderAuthenticationFilter;

/**
 * Объединённая конфигурация Spring Security для identity-service.
 * Открытые пути: /api/v1/auth/** (register, login, refresh), /api/v1/partners/applications POST.
 * Защищённые пути: /api/v1/super/** (SUPER_ADMIN), /api/v1/admin/** (ADMIN), /api/v1/partner/** (PARTNER).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final RoleHeaderAuthenticationFilter roleHeaderAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Публичные auth endpoints
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/password-reset/request",
                                "/api/v1/auth/password-reset/confirm",
                                "/api/v1/auth/confirm/email",
                                "/api/v1/auth/guest",
                                "/api/v1/auth/phone/request",
                                "/api/v1/auth/phone/confirm",
                                "/api/v1/auth/telegram").permitAll()

                        // Смена пароля требует аутентификации через Gateway headers
                        .requestMatchers(HttpMethod.PUT, "/api/v1/auth/change-password").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/confirm/request").authenticated()

                        // Публичный endpoint: заявки на партнёрство с лендинга
                        .requestMatchers(HttpMethod.POST, "/api/v1/partners/applications").permitAll()

                        // Actuator и Swagger
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Internal service-to-service endpoints (не маршрутизируются через Gateway)
                        .requestMatchers("/api/v1/internal/**").permitAll()

                        // Admin endpoints: управление пользователями и заявками
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // Доступ партнёра к своим сотрудникам
                        .requestMatchers("/api/v1/partner/**").hasAnyRole("PARTNER", "ADMIN", "SUPER_ADMIN")

                        // Все остальные — строго аутентифицированные
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(roleHeaderAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
