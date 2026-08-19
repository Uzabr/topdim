package uz.topdim.media.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class DefaultProcessRunner implements ProcessRunner {
    @Override
    public byte[] run(byte[] stdin, List<String> command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command).redirectErrorStream(false).start();
        try (OutputStream os = p.getOutputStream()) {
            os.write(stdin);
        }
        byte[] out = p.getInputStream().readAllBytes();
        if (!p.waitFor(20, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("vips timeout");
        }
        if (p.exitValue() != 0) {
            String err = new String(p.getErrorStream().readAllBytes());
            throw new IOException("vips exit " + p.exitValue() + ": " + err);
        }
        return out;
    }
}
