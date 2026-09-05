package uz.topdim.media.service;

import org.springframework.stereotype.Component;
import uz.topdim.media.config.MediaProperties;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Ресайз (без апскейла) + перекодирование в WebP + стрип метаданных через libvips CLI.
 * vipsthumbnail читает из stdin ([descriptor]), пишет webp в stdout:
 *   vipsthumbnail stdin --size {max}> -o .webp[Q={q},strip] --vips-concurrency=1
 * "{max}>" — не увеличивать, если изображение меньше цели (без апскейла).
 *
 * <p>Качество и целевые размеры вариантов берутся из {@link MediaProperties} (application.yml,
 * {@code media.webp-quality} / {@code media.sizes.<suffix>}). Если для варианта нет записи
 * в {@code media.sizes}, используется дефолт {@link ImageVariant#maxPx()}.
 */
@Component
public class ImageProcessor {

    private final ProcessRunner runner;
    private final MediaProperties properties;

    public ImageProcessor(ProcessRunner runner, MediaProperties properties) {
        this.runner = runner;
        this.properties = properties;
    }

    public Map<ImageVariant, byte[]> process(byte[] source) {
        Map<ImageVariant, byte[]> result = new EnumMap<>(ImageVariant.class);
        for (ImageVariant v : ImageVariant.values()) {
            result.put(v, encode(source, v));
        }
        return result;
    }

    private byte[] encode(byte[] source, ImageVariant variant) {
        int maxPx = properties.getSizes().getOrDefault(variant.suffix(), variant.maxPx());
        List<String> cmd = List.of(
                "vipsthumbnail", "stdin",
                "--size", maxPx + ">",              // ">" = не апскейлить
                "-o", ".webp[Q=" + properties.getWebpQuality() + ",strip]",
                "--vips-concurrency=1"
        );
        try {
            return runner.run(source, cmd);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageProcessingException("vips interrupted for " + variant, e);
        } catch (Exception e) {
            throw new ImageProcessingException("vips failed for " + variant, e);
        }
    }
}
