package uz.topdim.media.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Ресайз (без апскейла) + перекодирование в WebP + стрип метаданных через libvips CLI.
 * vipsthumbnail читает из stdin ([descriptor]), пишет webp в stdout:
 *   vipsthumbnail stdin --size {max}> -o .webp[Q={q},strip] --vips-concurrency=1
 * "{max}>" — не увеличивать, если изображение меньше цели (без апскейла).
 */
@Component
public class ImageProcessor {

    private final ProcessRunner runner;
    private final int quality;

    public ImageProcessor(ProcessRunner runner, @Value("${media.webp-quality:82}") int quality) {
        this.runner = runner;
        this.quality = quality;
    }

    public Map<ImageVariant, byte[]> process(byte[] source) {
        Map<ImageVariant, byte[]> result = new EnumMap<>(ImageVariant.class);
        for (ImageVariant v : ImageVariant.values()) {
            result.put(v, encode(source, v));
        }
        return result;
    }

    private byte[] encode(byte[] source, ImageVariant variant) {
        List<String> cmd = List.of(
                "vipsthumbnail", "stdin",
                "--size", variant.maxPx() + ">",              // ">" = не апскейлить
                "-o", ".webp[Q=" + quality + ",strip]",
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
