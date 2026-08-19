package uz.topdim.media.controller;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.media.dto.MediaUploadResponse;
import uz.topdim.media.service.ImageProcessor;
import uz.topdim.media.service.ImageUploadPolicy;
import uz.topdim.media.service.ImageVariant;
import uz.topdim.media.service.MediaStorageService;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaControllerTest {

    private MinioClient minioClient;
    private ImageUploadPolicy imageUploadPolicy;
    private ImageProcessor imageProcessor;
    private MediaStorageService storage;
    private MediaController controller;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        imageUploadPolicy = new ImageUploadPolicy();
        imageProcessor = mock(ImageProcessor.class);
        storage = mock(MediaStorageService.class);
        controller = new MediaController(minioClient, "media", imageUploadPolicy, imageProcessor, storage);
    }

    @Test
    void primaryConstructorIsAutowirable() {
        // Spring выбирает конструктор только при одном @Autowired среди нескольких.
        long autowired = Arrays.stream(MediaController.class.getDeclaredConstructors())
                .filter(c -> c.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class))
                .count();
        assertEquals(1, autowired, "ровно один конструктор должен быть @Autowired");
    }

    @Test
    void uploadReturnsIdUrlAndVariants() throws Exception {
        ImageUploadPolicy policy = mock(ImageUploadPolicy.class);
        when(policy.validate(any())).thenReturn(new ImageUploadPolicy.ValidatedImage("jpg", "image/jpeg"));
        ImageProcessor proc = mock(ImageProcessor.class);
        when(proc.process(any())).thenReturn(Map.of(
                ImageVariant.THUMB, new byte[]{1}, ImageVariant.CARD, new byte[]{2}, ImageVariant.FULL, new byte[]{3}));
        MediaStorageService store = mock(MediaStorageService.class);
        when(store.storeVariants(anyString(), any())).thenAnswer(i -> i.getArgument(0));

        MediaController c = new MediaController(policy, proc, store);
        var file = new MockMultipartFile("file", "logo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        var resp = c.upload(file);

        var body = resp.getBody().getData();
        assertNotNull(body.id());
        assertEquals("/api/v1/media/" + body.id(), body.url());
        assertEquals("/api/v1/media/" + body.id() + "/card", body.variants().get("card"));
        verify(store).storeVariants(eq(body.id()), any());
    }

    @Test
    void returnsBadRequestWithoutProcessingOrStorageForInvalidImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "not an image".getBytes()
        );

        ResponseEntity<ApiResponse<MediaUploadResponse>> response = controller.upload(file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Поддерживаются только JPEG, PNG, WebP и GIF");
        verify(imageProcessor, never()).process(any());
        verify(storage, never()).storeVariants(anyString(), any());
    }

    @Test
    void returnsPayloadTooLargeWithoutProcessingOrStorageForOversizedImage() throws Exception {
        byte[] content = new byte[(20 * 1024 * 1024) + 1];
        content[0] = (byte) 0x89;
        content[1] = 0x50;
        content[2] = 0x4E;
        content[3] = 0x47;
        content[4] = 0x0D;
        content[5] = 0x0A;
        content[6] = 0x1A;
        content[7] = 0x0A;

        ResponseEntity<ApiResponse<MediaUploadResponse>> response = controller.upload(
                new MockMultipartFile("file", "large.png", "image/png", content)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Размер файла не должен превышать 20 МБ");
        verify(imageProcessor, never()).process(any());
        verify(storage, never()).storeVariants(anyString(), any());
    }

    @Test
    void getVariantReturnsWebpWithImmutableCache() throws Exception {
        MediaStorageService store = mock(MediaStorageService.class);
        when(store.fetch("abc", ImageVariant.CARD))
                .thenReturn(new ByteArrayInputStream(new byte[]{'W', 'E', 'B', 'P'}));
        MediaController c = new MediaController(mock(ImageUploadPolicy.class), mock(ImageProcessor.class), store);

        ResponseEntity<?> resp = c.getVariant("abc", "card");

        assertEquals(MediaType.parseMediaType("image/webp"), resp.getHeaders().getContentType());
        assertEquals("public, max-age=31536000, immutable", resp.getHeaders().getCacheControl());
    }

    @Test
    void getUnknownVariantIs404() {
        MediaController c = new MediaController(mock(ImageUploadPolicy.class), mock(ImageProcessor.class), mock(MediaStorageService.class));
        assertEquals(404, c.getVariant("abc", "huge").getStatusCode().value());
    }

    @Test
    void getVariantIs404WhenStorageThrows() throws Exception {
        MediaStorageService store = mock(MediaStorageService.class);
        when(store.fetch("missing", ImageVariant.THUMB)).thenThrow(new RuntimeException("not found"));
        MediaController c = new MediaController(mock(ImageUploadPolicy.class), mock(ImageProcessor.class), store);

        assertEquals(404, c.getVariant("missing", "thumb").getStatusCode().value());
    }

    @Test
    void getDefaultDelegatesToFullVariantWhenIdHasNoExtension() throws Exception {
        when(storage.fetch("abc", ImageVariant.FULL))
                .thenReturn(new ByteArrayInputStream(new byte[]{'W', 'E', 'B', 'P'}));

        ResponseEntity<?> resp = controller.getDefault("abc");

        assertEquals(MediaType.parseMediaType("image/webp"), resp.getHeaders().getContentType());
        assertEquals("public, max-age=31536000, immutable", resp.getHeaders().getCacheControl());
    }

    @Test
    void getDefaultServesLegacyRawFileWithMetadataContentTypeAndImmutableCache() throws Exception {
        GetObjectResponse legacyResponse = new GetObjectResponse(
                new Headers.Builder().add("Content-Type", "image/png").build(),
                "media", "us-east-1", "logo.png",
                new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(legacyResponse);

        ResponseEntity<?> resp = controller.getDefault("logo.png");

        assertEquals(MediaType.IMAGE_PNG, resp.getHeaders().getContentType());
        assertEquals("public, max-age=31536000, immutable", resp.getHeaders().getCacheControl());
    }

    @Test
    void getDefaultLegacyRawIs404WhenObjectMissing() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new RuntimeException("not found"));

        assertEquals(404, controller.getDefault("missing.png").getStatusCode().value());
    }

    @Test
    void deleteFileDelegatesToStorageForBareUuid() throws Exception {
        ResponseEntity<ApiResponse<Void>> resp = controller.deleteFile("some-uuid");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().isSuccess()).isTrue();
        verify(storage).delete("some-uuid");
        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void deleteFileRemovesLegacyObjectDirectlyWhenFileNameHasExtension() throws Exception {
        ResponseEntity<ApiResponse<Void>> resp = controller.deleteFile("legacy.jpg");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().isSuccess()).isTrue();
        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertEquals("media", captor.getValue().bucket());
        assertEquals("legacy.jpg", captor.getValue().object());
        verify(storage, never()).delete(anyString());
    }
}
