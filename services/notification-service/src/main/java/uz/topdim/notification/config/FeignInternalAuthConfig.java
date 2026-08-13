package uz.topdim.notification.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class FeignInternalAuthConfig {

    @Bean
    public RequestInterceptor internalAuthInterceptor(
            @Value("${internal.auth-secret:}") String secret
    ) {
        return template -> {
            if (secret != null && !secret.isBlank()) {
                template.header("X-Gateway-Auth", secret);
            }
        };
    }
}
