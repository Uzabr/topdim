package uz.topdim.media.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import uz.topdim.media.config.MediaProperties;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Интеграционный тест {@link ImageProcessor} на РЕАЛЬНОМ libvips (не мок {@link ProcessRunner}).
 * Включается только если {@code vips} доступен в PATH — в CI/контейнере, где он установлен
 * (см. {@code build(media): установить libvips (vips-tools) в рантайм-образ}); на машинах без
 * vips тест автоматически SKIPPED, а не падает.
 */
@EnabledIf("vipsAvailable")
class ImageProcessorVipsIT {

    static boolean vipsAvailable() {
        try {
            return new ProcessBuilder("vips", "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void producesRealWebpVariantsWithoutUpscale() throws Exception {
        byte[] png = getClass().getResourceAsStream("/fixtures/sample-300.png").readAllBytes();
        MediaProperties props = new MediaProperties();
        props.setWebpQuality(82);
        Map<String, Integer> sizes = new LinkedHashMap<>();
        sizes.put("thumb", 200);
        sizes.put("card", 600);
        sizes.put("full", 1600);
        props.setSizes(sizes);
        ImageProcessor proc = new ImageProcessor(new DefaultProcessRunner(20), props);

        Map<ImageVariant, byte[]> out = proc.process(png);

        for (byte[] webp : out.values()) {
            assertTrue(webp.length > 0, "variant must not be empty");
            assertTrue(isWebp(webp), "магические байты RIFF....WEBP");
        }
        // FULL (1600) не апскейлит исходные 300px → ширина остаётся <= 300
        assertTrue(dimensions(out.get(ImageVariant.FULL)).width() <= 300);
        // THUMB (200, более агрессивное сжатие/меньший размер холста) не должен быть
        // существенно тяжелее FULL — грубая защита от полностью сломанной кодировки.
        assertTrue(out.get(ImageVariant.THUMB).length < out.get(ImageVariant.FULL).length + 1);
    }

    /** RIFF....WEBP — сигнатура контейнера WebP (RFC 9649 / libwebp container spec). */
    private static boolean isWebp(byte[] data) {
        if (data.length < 12) {
            return false;
        }
        return matches(data, 0, "RIFF") && matches(data, 8, "WEBP");
    }

    private static boolean matches(byte[] data, int offset, String ascii) {
        for (int i = 0; i < ascii.length(); i++) {
            if (data[offset + i] != (byte) ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private record Dimensions(int width, int height) {
    }

    /**
     * Парсит ширину/высоту из WebP-контейнера: сначала FourCC первого чанка после
     * "RIFF"+size+"WEBP" (12 байт заголовка), затем — в зависимости от чанка:
     * <ul>
     *   <li>{@code VP8X} (extended: alpha/ICC/EXIF/анимация) — canvas width/height-1,
     *       24-bit little-endian, сразу после 1 байта флагов и 3 зарезервированных байт;</li>
     *   <li>{@code VP8 } (simple lossy) — 3-байтный frame tag, 3-байтный sync code
     *       {@code 0x9d 0x01 0x2a}, затем 14-битные width/height (little-endian,
     *       старшие 2 бита — код масштаба, отбрасываются маской {@code 0x3FFF}).</li>
     * </ul>
     */
    private static Dimensions dimensions(byte[] data) {
        String fourCc = new String(data, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        int chunkPayload = 20; // 12 (RIFF header) + 4 (FourCC) + 4 (chunk size)
        if ("VP8X".equals(fourCc)) {
            int width = (readUnsigned24LE(data, chunkPayload + 4)) + 1;
            int height = (readUnsigned24LE(data, chunkPayload + 7)) + 1;
            return new Dimensions(width, height);
        }
        if ("VP8 ".equals(fourCc)) {
            int widthCode = readUnsigned16LE(data, chunkPayload + 6);
            int heightCode = readUnsigned16LE(data, chunkPayload + 8);
            return new Dimensions(widthCode & 0x3FFF, heightCode & 0x3FFF);
        }
        throw new IllegalArgumentException("unsupported WebP chunk for dimension parsing: " + fourCc);
    }

    private static int readUnsigned16LE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readUnsigned24LE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8) | ((data[offset + 2] & 0xFF) << 16);
    }
}
