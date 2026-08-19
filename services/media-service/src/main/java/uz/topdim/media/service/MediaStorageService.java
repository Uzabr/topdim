package uz.topdim.media.service;

import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

/**
 * Хранит и читает WebP-варианты изображений в MinIO.
 * Имя объекта: {@code {id}_{suffix}.webp}, где suffix — {@link ImageVariant#suffix()}.
 */
@Service
public class MediaStorageService {
    private final MinioClient minio;
    private final String bucket;

    public MediaStorageService(MinioClient minio, @Value("${minio.bucket}") String bucket) {
        this.minio = minio;
        this.bucket = bucket;
    }

    /**
     * Сохраняет каждый вариант как отдельный объект с contentType=image/webp.
     *
     * @return id (переданный на вход) — для удобства чейнинга вызовов
     * @throws ImageProcessingException если сохранение хотя бы одного варианта не удалось
     */
    public String storeVariants(String id, Map<ImageVariant, byte[]> variants) {
        try {
            for (var e : variants.entrySet()) {
                byte[] data = e.getValue();
                minio.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName(id, e.getKey()))
                        .stream(new ByteArrayInputStream(data), data.length, -1)
                        .contentType("image/webp")
                        .build());
            }
            return id;
        } catch (Exception ex) {
            throw new ImageProcessingException("store failed for " + id, ex);
        }
    }

    /**
     * Читает объект нужного варианта. Бросает исключение, если объект отсутствует.
     */
    public InputStream fetch(String id, ImageVariant variant) throws Exception {
        return minio.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(objectName(id, variant)).build());
    }

    /**
     * Удаляет все варианты изображения. Best-effort: ошибка удаления отдельного
     * варианта не прерывает удаление остальных.
     */
    public void delete(String id) {
        for (ImageVariant v : ImageVariant.values()) {
            try {
                minio.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket).object(objectName(id, v)).build());
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    private String objectName(String id, ImageVariant v) {
        return id + "_" + v.suffix() + ".webp";
    }
}
