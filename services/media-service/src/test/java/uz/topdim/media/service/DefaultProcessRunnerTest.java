package uz.topdim.media.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Проверяет реальный {@link DefaultProcessRunner} (без моков) через настоящие
 * POSIX-подпроцессы ({@code sh}/{@code cat}/{@code sleep}), доступные и на
 * macOS/Linux dev-машинах, и в CI. Ловит регресс к дедлоку записи-в-оба-конца
 * и отсутствию реального таймаута (I1 из код-ревью Task 3).
 */
class DefaultProcessRunnerTest {

    /**
     * Больше типичного ОС-буфера пайпа (~64KB) — старая реализация (пишет ВЕСЬ stdin,
     * ПОТОМ читает stdout) дедлочится на такой нагрузке: `cat` не может писать весь вывод
     * в переполненный пайп, поэтому перестаёт читать stdin, а наш writer блокируется на
     * записи в переполненный пайм stdin. JUnit-таймаут теста гарантирует, что регресс
     * к дедлоку завершится падением теста, а не зависанием всего сьюта.
     */
    @Test
    @Timeout(15)
    void doesNotDeadlockOnLargeOutput() throws Exception {
        DefaultProcessRunner runner = new DefaultProcessRunner(20);
        byte[] payload = new byte[250_000];
        new Random(42).nextBytes(payload);

        byte[] out = runner.run(payload, List.of("sh", "-c", "cat"));

        assertThat(out).isEqualTo(payload);
    }

    @Test
    @Timeout(10)
    void throwsAfterConfiguredTimeoutAndKillsProcess() {
        DefaultProcessRunner runner = new DefaultProcessRunner(1);

        assertThatThrownBy(() -> runner.run(new byte[0], List.of("sh", "-c", "sleep 60")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    @Timeout(10)
    void throwsWithExitCodeOnNonZeroExit() {
        DefaultProcessRunner runner = new DefaultProcessRunner(20);

        assertThatThrownBy(() -> runner.run(new byte[0], List.of("sh", "-c", "exit 3")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("3");
    }

    @Test
    @Timeout(10)
    void returnsStdoutOnSuccessfulSmallCommand() throws Exception {
        DefaultProcessRunner runner = new DefaultProcessRunner(20);

        byte[] out = runner.run(new byte[0], List.of("sh", "-c", "printf hello"));

        assertThat(new String(out)).isEqualTo("hello");
    }
}
