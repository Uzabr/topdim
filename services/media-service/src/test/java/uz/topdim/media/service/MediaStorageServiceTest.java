package uz.topdim.media.service;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaStorageServiceTest {

    @Test
    void storesEachVariantAsWebp() throws Exception {
        MinioClient minio = mock(MinioClient.class);
        MediaStorageService svc = new MediaStorageService(minio, "topdim-media");
        Map<ImageVariant, byte[]> variants = Map.of(
                ImageVariant.THUMB, new byte[]{1}, ImageVariant.CARD, new byte[]{2}, ImageVariant.FULL, new byte[]{3});

        String id = svc.storeVariants("abc", variants);

        assertEquals("abc", id);
        ArgumentCaptor<PutObjectArgs> cap = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minio, times(3)).putObject(cap.capture());
        for (PutObjectArgs a : cap.getAllValues()) {
            assertEquals("image/webp", a.contentType());
            assertEquals("topdim-media", a.bucket());
        }
        assertTrue(cap.getAllValues().stream()
                .anyMatch(a -> a.object().equals("abc_thumb.webp")));
        assertTrue(cap.getAllValues().stream()
                .anyMatch(a -> a.object().equals("abc_card.webp")));
        assertTrue(cap.getAllValues().stream()
                .anyMatch(a -> a.object().equals("abc_full.webp")));
    }

    @Test
    void wrapsMinioFailureDuringStoreAsImageProcessingException() throws Exception {
        MinioClient minio = mock(MinioClient.class);
        when(minio.putObject(any())).thenThrow(new IOException("minio down"));
        MediaStorageService svc = new MediaStorageService(minio, "topdim-media");

        assertThrows(ImageProcessingException.class,
                () -> svc.storeVariants("abc", Map.of(ImageVariant.THUMB, new byte[]{1})));
    }

    @Test
    void fetchReadsRequestedVariantFromBucket() throws Exception {
        MinioClient minio = mock(MinioClient.class);
        byte[] data = {9, 8, 7};
        GetObjectResponse response = new GetObjectResponse(
                Headers.of(), "topdim-media", "us-east-1", "abc_card.webp", new ByteArrayInputStream(data));
        when(minio.getObject(any(GetObjectArgs.class))).thenReturn(response);
        MediaStorageService svc = new MediaStorageService(minio, "topdim-media");

        InputStream in = svc.fetch("abc", ImageVariant.CARD);

        assertArrayEquals(data, in.readAllBytes());
        ArgumentCaptor<GetObjectArgs> cap = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(minio).getObject(cap.capture());
        assertEquals("topdim-media", cap.getValue().bucket());
        assertEquals("abc_card.webp", cap.getValue().object());
    }

    @Test
    void fetchPropagatesFailureWhenObjectMissing() throws Exception {
        MinioClient minio = mock(MinioClient.class);
        when(minio.getObject(any(GetObjectArgs.class))).thenThrow(new IOException("not found"));
        MediaStorageService svc = new MediaStorageService(minio, "topdim-media");

        assertThrows(IOException.class, () -> svc.fetch("missing", ImageVariant.THUMB));
    }

    @Test
    void deleteRemovesAllVariantsForId() throws Exception {
        MinioClient minio = mock(MinioClient.class);
        MediaStorageService svc = new MediaStorageService(minio, "topdim-media");

        svc.delete("abc");

        ArgumentCaptor<RemoveObjectArgs> cap = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minio, times(ImageVariant.values().length)).removeObject(cap.capture());
        List<String> objects = cap.getAllValues().stream().map(RemoveObjectArgs::object).toList();
        assertTrue(objects.contains("abc_thumb.webp"));
        assertTrue(objects.contains("abc_card.webp"));
        assertTrue(objects.contains("abc_full.webp"));
    }

    @Test
    void deleteIsBestEffortWhenRemovalFails() throws Exception {
        MinioClient minio = mock(MinioClient.class);
        doThrow(new IOException("gone")).when(minio).removeObject(any(RemoveObjectArgs.class));
        MediaStorageService svc = new MediaStorageService(minio, "topdim-media");

        svc.delete("abc");

        verify(minio, times(ImageVariant.values().length)).removeObject(any(RemoveObjectArgs.class));
    }
}
