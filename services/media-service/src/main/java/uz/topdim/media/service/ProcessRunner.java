package uz.topdim.media.service;

import java.io.IOException;
import java.util.List;

public interface ProcessRunner {
    /** Запускает команду, подаёт stdin, возвращает stdout. Бросает при ненулевом коде выхода. */
    byte[] run(byte[] stdin, List<String> command) throws IOException, InterruptedException;
}
