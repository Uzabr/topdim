package uz.topdim.media.controller;

import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.media.dto.MediaUploadResponse;
import uz.topdim.media.service.ImageProcessingException;
import uz.topdim.media.service.ImageProcessor;
import uz.topdim.media.service.ImageUploadPolicy;
import uz.topdim.media.service.ImageUploadTooLargeException;
import uz.topdim.media.service.ImageVariant;
import uz.topdim.media.service.InvalidImageUploadException;
import uz.topdim.media.service.MediaStorageService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST контроллер медиа файлов.
 * Endpoints: upload (POST), download (GET), delete (DELETE).
 * Файлы хранятся в MinIO (S3-compatible).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MinioClient minioClient;
    private final String bucketName;
    private final ImageUploadPolicy imageUploadPolicy;
    private final ImageProcessor imageProcessor;
    private final MediaStorageService storage;

    @Autowired
    public MediaController(
            @Value("${minio.url}") String minioUrl,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey,
            @Value("${minio.bucket}") String bucket,
            ImageUploadPolicy imageUploadPolicy,
            ImageProcessor imageProcessor,
            MediaStorageService storage
    ) {
        this(MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(accessKey, secretKey)
                .build(), bucket, imageUploadPolicy, imageProcessor, storage);
        initBucket();
    }

    MediaController(MinioClient minioClient, String bucketName, ImageUploadPolicy imageUploadPolicy,
                     ImageProcessor imageProcessor, MediaStorageService storage) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        this.imageUploadPolicy = imageUploadPolicy;
        this.imageProcessor = imageProcessor;
        this.storage = storage;
    }

    /**
     * Тестовый конструктор для проверки {@link #upload(MultipartFile)} в изоляции от MinIO
     * (используется только для legacy {@code getFile}/{@code deleteFile}, которые Task 5 не трогает).
     */
    MediaController(ImageUploadPolicy imageUploadPolicy, ImageProcessor imageProcessor, MediaStorageService storage) {
        this(null, null, imageUploadPolicy, imageProcessor, storage);
    }

    private void initBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            log.warn("Could not initialize MinIO bucket: {}", e.getMessage());
        }
    }

    /**
     * POST /api/v1/media/upload — Загрузка файла.
     * Валидирует изображение, генерирует WebP-варианты (thumb/card/full) через
     * {@link ImageProcessor} и сохраняет их через {@link MediaStorageService}.
     *
     * @param file multipart файл для загрузки
     * @return id файла, канонический URL и URL-ы всех вариантов
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaUploadResponse>> upload(@RequestParam("file") MultipartFile file) {
        try {
            imageUploadPolicy.validate(file);
        } catch (ImageUploadTooLargeException e) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (InvalidImageUploadException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }

        byte[] source;
        try {
            source = file.getBytes();
        } catch (IOException e) {
            throw new ImageProcessingException("read failed", e);
        }

        Map<ImageVariant, byte[]> variants = imageProcessor.process(source);
        String id = UUID.randomUUID().toString();
        storage.storeVariants(id, variants);

        Map<String, String> variantUrls = new LinkedHashMap<>();
        for (ImageVariant v : ImageVariant.values()) {
            variantUrls.put(v.suffix(), "/api/v1/media/" + id + "/" + v.suffix());
        }
        var body = new MediaUploadResponse(id, "/api/v1/media/" + id, variantUrls);
        return ResponseEntity.ok(ApiResponse.success("Файл загружен", body));
    }

    /**
     * GET /api/v1/media/{fileName} — Скачивание файла.
     * Возвращает файл из MinIO как byte[].
     *
     * @param fileName имя файла в хранилище
     * @return файл с правильным Content-Type
     */
    @GetMapping("/{fileName}")
    public ResponseEntity<byte[]> getFile(@PathVariable String fileName) {
        try {
            GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
            byte[] content = response.readAllBytes();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(content);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * DELETE /api/v1/media/{fileName} — Удаление файла.
     *
     * @param fileName имя файла для удаления
     * @return 200 OK при успешном удалении
     */
    @DeleteMapping("/{fileName}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable String fileName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
            return ResponseEntity.ok(ApiResponse.success("Файл удалён", null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Ошибка удаления: " + e.getMessage()));
        }
    }
}
