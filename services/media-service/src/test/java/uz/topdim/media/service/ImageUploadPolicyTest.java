package uz.topdim.media.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageUploadPolicyTest {

    private final ImageUploadPolicy policy = new ImageUploadPolicy();

    @Test
    void acceptsJpegWhenDeclaredTypeMatchesFileSignature() {
        MultipartFile file = file("photo.jpg", "image/jpeg", bytes(
                0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10
        ));

        ImageUploadPolicy.ValidatedImage image = policy.validate(file);

        assertThat(image.extension()).isEqualTo("jpg");
        assertThat(image.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void acceptsPngWebpAndGifByTheirSignatures() {
        assertThat(policy.validate(file("logo.png", "image/png", bytes(
                0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        ))).extension()).isEqualTo("png");

        assertThat(policy.validate(file("cover.webp", "image/webp", bytes(
                0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50
        ))).extension()).isEqualTo("webp");

        assertThat(policy.validate(file("banner.gif", "image/gif", bytes(
                0x47, 0x49, 0x46, 0x38, 0x39, 0x61
        ))).extension()).isEqualTo("gif");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> policy.validate(file("empty.png", "image/png", new byte[0])))
                .isInstanceOf(InvalidImageUploadException.class)
                .hasMessage("Файл не должен быть пустым");
    }

    @Test
    void rejectsFileLargerThanTwentyMegabytes() {
        byte[] oversized = new byte[(20 * 1024 * 1024) + 1];
        oversized[0] = (byte) 0x89;
        oversized[1] = 0x50;
        oversized[2] = 0x4E;
        oversized[3] = 0x47;
        oversized[4] = 0x0D;
        oversized[5] = 0x0A;
        oversized[6] = 0x1A;
        oversized[7] = 0x0A;

        assertThatThrownBy(() -> policy.validate(file("large.png", "image/png", oversized)))
                .isInstanceOf(ImageUploadTooLargeException.class)
                .hasMessage("Размер файла не должен превышать 20 МБ");
    }

    @Test
    void acceptsFileAtExactTwentyMegabyteBoundary() {
        byte[] boundary = new byte[20 * 1024 * 1024];
        boundary[0] = (byte) 0x89;
        boundary[1] = 0x50;
        boundary[2] = 0x4E;
        boundary[3] = 0x47;
        boundary[4] = 0x0D;
        boundary[5] = 0x0A;
        boundary[6] = 0x1A;
        boundary[7] = 0x0A;

        assertThat(policy.validate(file("boundary.png", "image/png", boundary)).extension())
                .isEqualTo("png");
    }

    @Test
    void rejectsAllowedMimeWhenSignatureIsNotAnImage() {
        assertThatThrownBy(() -> policy.validate(file(
                "payload.png", "image/png", "not an image".getBytes()
        )))
                .isInstanceOf(InvalidImageUploadException.class)
                .hasMessage("Поддерживаются только JPEG, PNG, WebP и GIF");
    }

    @Test
    void rejectsMimeThatDoesNotMatchDetectedImage() {
        assertThatThrownBy(() -> policy.validate(file("fake.png", "image/png", bytes(
                0xFF, 0xD8, 0xFF, 0xE0
        ))))
                .isInstanceOf(InvalidImageUploadException.class)
                .hasMessage("Тип файла не соответствует его содержимому");
    }

    @Test
    void rejectsSvgEvenWhenDeclaredAsImage() {
        assertThatThrownBy(() -> policy.validate(file(
                "unsafe.svg", "image/svg+xml", "<svg><script/></svg>".getBytes()
        )))
                .isInstanceOf(InvalidImageUploadException.class)
                .hasMessage("Поддерживаются только JPEG, PNG, WebP и GIF");
    }

    private static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
