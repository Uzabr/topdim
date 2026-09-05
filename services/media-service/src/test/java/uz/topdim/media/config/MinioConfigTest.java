package uz.topdim.media.config;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.topdim.media.service.MediaStorageService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Доказывает, что {@link MinioConfig} чинит баг из task-7-report.md ("Concerns"):
 * без {@code @Bean MinioClient} контекст с {@link MediaStorageService} не поднимается
 * ({@code UnsatisfiedDependencyException}, т.к. конструктору {@code MediaStorageService}
 * нечем удовлетворить параметр {@code MinioClient}).
 *
 * <p>Используется лёгкий {@link ApplicationContextRunner} (без Eureka/полного
 * {@code MediaServiceApplication}) — только {@link MinioConfig} + {@link MediaStorageService}
 * с нужными {@code minio.*} свойствами.
 */
class MinioConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues(
                    "minio.url=http://localhost:9000",
                    "minio.access-key=x",
                    "minio.secret-key=y",
                    "minio.bucket=b"
            );

    @Configuration
    static class MediaStorageServiceOnlyConfig {
        @Bean
        MediaStorageService mediaStorageService(MinioClient minio,
                                                 @Value("${minio.bucket}") String bucket) {
            return new MediaStorageService(minio, bucket);
        }
    }

    @Test
    void contextStartsAndWiresMinioClientAndMediaStorageServiceWhenMinioConfigIsPresent() {
        contextRunner
                .withUserConfiguration(MinioConfig.class, MediaStorageServiceOnlyConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MinioClient.class);
                    assertThat(context).hasSingleBean(MediaStorageService.class);
                });
    }

    /**
     * Контрольная проверка: тот же runner БЕЗ {@link MinioConfig} должен падать —
     * иначе тест выше ничего не доказывает. Это воспроизводит ровно тот баг, который
     * фиксит {@link MinioConfig} (см. task-7-report.md, "Concerns").
     */
    @Test
    void contextFailsWithoutMinioConfigBecauseMinioClientBeanIsMissing() {
        contextRunner
                .withUserConfiguration(MediaStorageServiceOnlyConfig.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isNotNull();
                });
    }
}
