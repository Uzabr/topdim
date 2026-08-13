package uz.topdim.media.service;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;

@Component
public class ImageUploadPolicy {

    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final int SIGNATURE_LENGTH = 12;

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageUploadException("Файл не должен быть пустым");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ImageUploadTooLargeException("Размер файла не должен превышать 20 МБ");
        }

        ImageFormat format = detectFormat(readSignature(file));
        if (format == null) {
            throw new InvalidImageUploadException("Поддерживаются только JPEG, PNG, WebP и GIF");
        }
        if (!format.contentTypes.contains(file.getContentType())) {
            throw new InvalidImageUploadException("Тип файла не соответствует его содержимому");
        }
        return new ValidatedImage(format.extension, format.canonicalContentType);
    }

    private byte[] readSignature(MultipartFile file) {
        try (InputStream stream = file.getInputStream()) {
            return stream.readNBytes(SIGNATURE_LENGTH);
        } catch (IOException e) {
            throw new InvalidImageUploadException("Не удалось прочитать файл", e);
        }
    }

    private ImageFormat detectFormat(byte[] bytes) {
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return ImageFormat.JPEG;
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return ImageFormat.PNG;
        }
        if (startsWith(bytes, 0x47, 0x49, 0x46, 0x38, 0x37, 0x61)
                || startsWith(bytes, 0x47, 0x49, 0x46, 0x38, 0x39, 0x61)) {
            return ImageFormat.GIF;
        }
        if (bytes.length >= 12
                && startsWith(bytes, 0x52, 0x49, 0x46, 0x46)
                && matchesAt(bytes, 8, 0x57, 0x45, 0x42, 0x50)) {
            return ImageFormat.WEBP;
        }
        return null;
    }

    private boolean startsWith(byte[] actual, int... expected) {
        return matchesAt(actual, 0, expected);
    }

    private boolean matchesAt(byte[] actual, int offset, int... expected) {
        if (actual.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if ((actual[offset + index] & 0xFF) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    public record ValidatedImage(String extension, String contentType) {
    }

    private enum ImageFormat {
        JPEG("jpg", "image/jpeg", "image/jpeg", "image/jpg"),
        PNG("png", "image/png", "image/png"),
        WEBP("webp", "image/webp", "image/webp"),
        GIF("gif", "image/gif", "image/gif");

        private final String extension;
        private final String canonicalContentType;
        private final Set<String> contentTypes;

        ImageFormat(String extension, String canonicalContentType, String... contentTypes) {
            this.extension = extension;
            this.canonicalContentType = canonicalContentType;
            this.contentTypes = Set.copyOf(Arrays.asList(contentTypes));
        }
    }
}
