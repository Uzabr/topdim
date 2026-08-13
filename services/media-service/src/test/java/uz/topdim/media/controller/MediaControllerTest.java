package uz.topdim.media.controller;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.media.service.ImageUploadPolicy;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaControllerTest {

    private MinioClient minioClient;
    private MediaController controller;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        controller = new MediaController(minioClient, "media", new ImageUploadPolicy());
    }

    @Test
    void uploadsValidatedImageWithServerGeneratedSafeNameAndCanonicalType() throws Exception {
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../logo dangerous.svg.png",
                "image/png",
                bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        );

        ResponseEntity<ApiResponse<Map<String, String>>> response = controller.upload(file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        String fileName = response.getBody().getData().get("fileName");
        assertThat(fileName).matches("[0-9a-f-]{36}\\.png");
        assertThat(fileName).doesNotContain("logo", "..", "/");
        assertThat(response.getBody().getData().get("url")).isEqualTo("/api/v1/media/" + fileName);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertThat(captor.getValue().object()).isEqualTo(fileName);
        assertThat(captor.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void returnsBadRequestWithoutStorageCallForInvalidImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "not an image".getBytes()
        );

        ResponseEntity<ApiResponse<Map<String, String>>> response = controller.upload(file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Поддерживаются только JPEG, PNG, WebP и GIF");
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void returnsPayloadTooLargeWithoutStorageCallForOversizedImage() throws Exception {
        byte[] content = new byte[(20 * 1024 * 1024) + 1];
        content[0] = (byte) 0x89;
        content[1] = 0x50;
        content[2] = 0x4E;
        content[3] = 0x47;
        content[4] = 0x0D;
        content[5] = 0x0A;
        content[6] = 0x1A;
        content[7] = 0x0A;

        ResponseEntity<ApiResponse<Map<String, String>>> response = controller.upload(
                new MockMultipartFile("file", "large.png", "image/png", content)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Размер файла не должен превышать 20 МБ");
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
