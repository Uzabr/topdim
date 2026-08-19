package uz.topdim.media.service;

import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Хранит и читает WebP-варианты изображений в MinIO.
 * Имя объекта: {@code {id}_{suffix}.webp}, где suffix — {@link ImageVariant#suffix()}.
 */
@Slf4j
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
     * Если сохранение одного из вариантов падает, уже записанные для этого id
     * варианты компенсирующе удаляются (best-effort), чтобы не оставлять
     * "осиротевшие" объекты в MinIO, после чего бросается {@link ImageProcessingException}.
     *
     * @return id (переданный на вход) — для удобства чейнинга вызовов
     * @throws ImageProcessingException если сохранение хотя бы одного варианта не удалось
     */
    public String storeVariants(String id, Map<ImageVariant, byte[]> variants) {
        List<ImageVariant> written = new ArrayList<>();
        try {
            for (var e : variants.entrySet()) {
                byte[] data = e.getValue();
                minio.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName(id, e.getKey()))
                        .stream(new ByteArrayInputStream(data), data.length, -1)
                        .contentType("image/webp")
                        .build());
                written.add(e.getKey());
            }
            return id;
        } catch (Exception ex) {
            compensate(id, written);
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
     * варианта не прерывает удаление остальных (только логируется).
     */
    public void delete(String id) {
        for (ImageVariant v : ImageVariant.values()) {
            removeBestEffort(id, v);
        }
    }

    /**
     * Компенсирующее удаление уже записанных вариантов после неудачной попытки
     * {@link #storeVariants}. Best-effort — как и {@link #delete}.
     */
    private void compensate(String id, List<ImageVariant> written) {
        for (ImageVariant v : written) {
            removeBestEffort(id, v);
        }
    }

    private void removeBestEffort(String id, ImageVariant v) {
        try {
            minio.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket).object(objectName(id, v)).build());
        } catch (Exception ex) {
            log.warn("Could not remove MinIO object {} (id={}, variant={})", objectName(id, v), id, v, ex);
        }
    }

    private String objectName(String id, ImageVariant v) {
        return id + "_" + v.suffix() + ".webp";
    }
}
