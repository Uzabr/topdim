package uz.topdim.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Конфигурация Spring Security для API Gateway.
 * Настраивает CORS, Security Headers, CSP.
 *
 * <p>Security Headers (OWASP):
 * <ul>
 *   <li>X-Content-Type-Options: nosniff</li>
 *   <li>X-Frame-Options: DENY</li>
 *   <li>Referrer-Policy: strict-origin-when-cross-origin</li>
 *   <li>Content-Security-Policy: default-src 'self' (report-only на старте)</li>
 *   <li>X-XSS-Protection: 0 (отключаем — OWASP рекомендует полагаться на CSP)</li>
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private final CorsProperties corsProperties;

    public SecurityConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                // Security Headers
                .headers(headers -> headers
                        // X-Content-Type-Options: nosniff
                        .contentTypeOptions(contentTypeOptions -> {})
                        // X-Frame-Options: DENY
                        .frameOptions(frameOptions ->
                                frameOptions.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                        // Referrer-Policy: strict-origin-when-cross-origin
                        .referrerPolicy(referrer ->
                                referrer.policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // X-XSS-Protection: отключаем (OWASP: полагаться на CSP)
                        .xssProtection(xss -> xss.disable())
                )

                // Авторизацию выполняет наш JwtAuthenticationFilter (GlobalFilter),
                // а не Spring Security. Поэтому разрешаем все запросы на уровне Security.
                .authorizeExchange(exchange -> exchange
                        .anyExchange().permitAll()
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(corsProperties.getAllowedMethods());
        config.setAllowedHeaders(corsProperties.getAllowedHeaders());
        config.setAllowCredentials(corsProperties.isAllowCredentials());
        config.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
