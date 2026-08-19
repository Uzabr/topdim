package uz.topdim.media.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Проверяет биндинг {@code media.*} из application.yml/переопределений в {@link MediaProperties}.
 *
 * <p>Контекст намеренно сужен до {@link Config} (только регистрация {@link MediaProperties})
 * вместо полного {@code MediaServiceApplication}: полный контекст сейчас не поднимается —
 * {@code MediaStorageService} требует бин {@code MinioClient}, которого нет ни одного
 * {@code @Bean}-провайдера (см. task-7-report.md, раздел "Concerns" — предсуществующий баг,
 * не относится к этой задаче). Для теста биндинга свойств полный контекст и не нужен.
 */
@SpringBootTest(classes = MediaPropertiesTest.Config.class,
        properties = {"media.webp-quality=80", "media.sizes.card=640", "media.max-input-pixels=8000"})
class MediaPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(MediaProperties.class)
    static class Config {
    }

    @Autowired
    MediaProperties props;

    @Test
    void binds() {
        assertEquals(80, props.getWebpQuality());
        assertEquals(640, props.getSizes().get("card"));
        assertEquals(8000, props.getMaxInputPixels());
    }
}
