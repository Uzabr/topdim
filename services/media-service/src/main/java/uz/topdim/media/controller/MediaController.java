package uz.topdim.media.controller;

import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
import java.io.InputStream;
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

    /**
     * Директивы Cache-Control заданы литеральной строкой (а не через
     * {@link org.springframework.http.CacheControl}), потому что
     * {@code CacheControl.maxAge(...).cachePublic().immutable()} всегда сериализует
     * директивы в фиксированном внутреннем порядке ({@code "max-age=..., public, immutable"}),
     * независимо от порядка вызова билдера — это не совпадает с контрактом
     * {@code "public, max-age=31536000, immutable"} из спецификации API. Семантически
     * порядок директив Cache-Control не важен (RFC 7234), но контракт и тесты сравнивают
     * точную строку, поэтому фиксируем её явно.
     */
    private static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";

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
     * Тестовый конструктор для проверки {@link #upload(MultipartFile)}, {@link #getVariant}
     * и {@link #getDefault} в изоляции от MinIO ({@code minioClient} остаётся {@code null} —
     * не годится для {@code deleteFile}/legacy-{@code getDefault}, которым он нужен).
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
     * GET /api/v1/media/{id} — дефолтная отдача (full-вариант) или legacy-совместимость.
     * Если {@code id} содержит "." (расширение) — это старый путь до Task 6, отдаём
     * сырой объект из MinIO как есть ({@link #legacyRaw(String)}). Иначе — это UUID,
     * заведённый через {@link #upload}, отдаём full-вариант через {@link #getVariant}.
     *
     * @param id идентификатор файла (UUID) либо legacy имя файла с расширением
     * @return WebP full-вариант либо сырой legacy-объект
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getDefault(@PathVariable String id) {
        return id.contains(".") ? legacyRaw(id) : getVariant(id, "full");
    }

    /**
     * GET /api/v1/media/{id}/{variant} — отдача конкретного WebP-варианта.
     * Неизвестный variant или отсутствующий объект → 404.
     *
     * @param id      идентификатор файла (UUID), под которым сохранены варианты
     * @param variant имя варианта ({@code thumb}/{@code card}/{@code full}, регистронезависимо)
     * @return WebP-байты с {@code Content-Type: image/webp} и immutable-кэшем, либо 404
     */
    @GetMapping("/{id}/{variant}")
    public ResponseEntity<?> getVariant(@PathVariable String id, @PathVariable String variant) {
        ImageVariant v;
        try {
            v = ImageVariant.valueOf(variant.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        try (InputStream in = storage.fetch(id, v)) {
            byte[] body = in.readAllBytes(); // TODO Task-follow-up: заменить на StreamingResponseBody
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("image/webp"))
                    .header(HttpHeaders.CACHE_CONTROL, IMMUTABLE_CACHE_CONTROL)
                    .body(body);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Legacy-путь (до Task 6): отдаёт объект из MinIO по имени как есть, определяя
     * {@code Content-Type} из метаданных самого объекта (а не жёстко octet-stream, как
     * было раньше), плюс тот же immutable-кэш, что и у новых WebP-вариантов.
     *
     * @param fileName имя файла в хранилище (содержит расширение)
     * @return сырое содержимое объекта либо 404
     */
    private ResponseEntity<?> legacyRaw(String fileName) {
        try {
            GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
            byte[] content = response.readAllBytes();
            String contentType = response.headers().get("Content-Type");
            MediaType mediaType = contentType != null
                    ? MediaType.parseMediaType(contentType)
                    : MediaType.APPLICATION_OCTET_STREAM;
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CACHE_CONTROL, IMMUTABLE_CACHE_CONTROL)
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
