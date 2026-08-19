package uz.topdim.media.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.topdim.media.config.MediaProperties;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageProcessorTest {

    /** Отражает дефолты из application.yml (media.webp-quality=82, media.sizes.*). */
    private static MediaProperties mediaProperties() {
        MediaProperties props = new MediaProperties();
        props.setWebpQuality(82);
        props.setMaxInputPixels(8000);
        Map<String, Integer> sizes = new LinkedHashMap<>();
        sizes.put("thumb", 200);
        sizes.put("card", 600);
        sizes.put("full", 1600);
        props.setSizes(sizes);
        return props;
    }

    @Test
    void processesAllVariantsViaVips() throws Exception {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(), anyList())).thenReturn(new byte[]{'W', 'E', 'B', 'P'});
        ImageProcessor proc = new ImageProcessor(runner, mediaProperties());

        Map<ImageVariant, byte[]> out = proc.process(new byte[]{1, 2, 3});

        assertEquals(3, out.size());
        assertArrayEquals(new byte[]{'W', 'E', 'B', 'P'}, out.get(ImageVariant.CARD));

        ArgumentCaptor<List<String>> cmd = ArgumentCaptor.forClass(List.class);
        verify(runner, times(3)).run(any(), cmd.capture());
        String joined = String.join(" ", cmd.getAllValues().get(0));
        assertTrue(joined.contains("1600") || cmd.getAllValues().stream()
                .anyMatch(c -> String.join(" ", c).contains("1600")));
    }

    @Test
    void usesSizeFromMediaPropertiesWhenPresent() throws Exception {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(), anyList())).thenReturn(new byte[]{'W', 'E', 'B', 'P'});
        MediaProperties props = mediaProperties();
        props.getSizes().put("card", 640); // переопределение размера через конфиг
        ImageProcessor proc = new ImageProcessor(runner, props);

        proc.process(new byte[]{1, 2, 3});

        ArgumentCaptor<List<String>> cmd = ArgumentCaptor.forClass(List.class);
        verify(runner, times(3)).run(any(), cmd.capture());
        assertTrue(cmd.getAllValues().stream().anyMatch(c -> String.join(" ", c).contains("640>")));
    }

    @Test
    void fallsBackToImageVariantMaxPxWhenSizeMissingFromConfig() throws Exception {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(), anyList())).thenReturn(new byte[]{'W', 'E', 'B', 'P'});
        MediaProperties props = new MediaProperties(); // sizes не заданы
        props.setWebpQuality(82);
        ImageProcessor proc = new ImageProcessor(runner, props);

        proc.process(new byte[]{1, 2, 3});

        ArgumentCaptor<List<String>> cmd = ArgumentCaptor.forClass(List.class);
        verify(runner, times(3)).run(any(), cmd.capture());
        assertTrue(cmd.getAllValues().stream().anyMatch(c -> String.join(" ", c).contains("1600>")));
    }

    @Test
    void wrapsRunnerFailureAsImageProcessingException() throws Exception {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(), anyList())).thenThrow(new IOException("vips missing"));
        ImageProcessor proc = new ImageProcessor(runner, mediaProperties());

        assertThrows(ImageProcessingException.class, () -> proc.process(new byte[]{1}));
    }
}
