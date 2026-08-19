package uz.topdim.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Настройки обработки изображений (media-service) из application.yml.
 *
 * <p>Пример:
 * <pre>
 * media:
 *   webp-quality: 82
 *   max-input-pixels: 8000
 *   sizes:
 *     thumb: 200
 *     card: 600
 *     full: 1600
 * </pre>
 *
 * <p>{@code sizes} — максимальная сторона (px) для каждого варианта ({@link
 * uz.topdim.media.service.ImageVariant#suffix()}), используется {@link
 * uz.topdim.media.service.ImageProcessor} вместо хардкода в {@code ImageVariant}.
 * {@code maxInputPixels} зарезервирован под будущую защиту от decompression-bomb
 * на входе (не применяется в этой задаче).
 */
@ConfigurationProperties(prefix = "media")
public class MediaProperties {

    private int webpQuality = 82;
    private int maxInputPixels = 8000;
    private Map<String, Integer> sizes = new LinkedHashMap<>();

    public int getWebpQuality() {
        return webpQuality;
    }

    public void setWebpQuality(int webpQuality) {
        this.webpQuality = webpQuality;
    }

    public int getMaxInputPixels() {
        return maxInputPixels;
    }

    public void setMaxInputPixels(int maxInputPixels) {
        this.maxInputPixels = maxInputPixels;
    }

    public Map<String, Integer> getSizes() {
        return sizes;
    }

    public void setSizes(Map<String, Integer> sizes) {
        this.sizes = sizes;
    }
}
