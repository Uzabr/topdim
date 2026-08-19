package uz.topdim.media.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
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

    @Test
    void processesAllVariantsViaVips() throws Exception {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(), anyList())).thenReturn(new byte[]{'W', 'E', 'B', 'P'});
        ImageProcessor proc = new ImageProcessor(runner, 82);

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
    void wrapsRunnerFailureAsImageProcessingException() throws Exception {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(), anyList())).thenThrow(new IOException("vips missing"));
        ImageProcessor proc = new ImageProcessor(runner, 82);

        assertThrows(ImageProcessingException.class, () -> proc.process(new byte[]{1}));
    }
}
