package uz.topdim.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import uz.topdim.media.config.MediaProperties;

/**
 * Точка входа в Media Service.
 * Загрузка и хранение файлов (MinIO).
 * Порт: 8087
 */
@SpringBootApplication
@EnableConfigurationProperties(MediaProperties.class)
public class MediaServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MediaServiceApplication.class, args);
    }
}
