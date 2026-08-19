package uz.topdim.media.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Регистрирует {@link MinioClient} как Spring-бин.
 *
 * <p>До этой конфигурации ни один {@code @Bean}-провайдер {@link MinioClient} не существовал:
 * {@code MediaController} строил свой собственный {@code MinioClient} локально
 * (через {@code MinioClient.builder()...build()} внутри конструктора) и никогда не публиковал
 * его в {@code ApplicationContext}, а {@code MediaStorageService} независимо требует
 * {@code MinioClient}-бин через конструкторную инъекцию. Из-за этого полный контекст
 * приложения не поднимался ({@code UnsatisfiedDependencyException} /
 * {@code NoSuchBeanDefinitionException} для {@code MinioClient}) — см. task-7-report.md,
 * раздел "Concerns".
 *
 * <p>Использует те же свойства ({@code minio.url}, {@code minio.access-key},
 * {@code minio.secret-key}), что и {@code MediaController}, чтобы не менять поведение
 * ни контроллера, ни {@code application.yml}.
 */
@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(
            @Value("${minio.url}") String url,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey
    ) {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }
}
