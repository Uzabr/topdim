package uz.topdim.media.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

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
     *
     * <p>{@code threadMode = SEPARATE_THREAD} обязателен: с дефолтным SAME_THREAD JUnit
     * проверяет прошедшее время только ПОСЛЕ того, как вызов вернётся, — при истинном
     * дедлоке (блокирующий native-вызов) вызов никогда не вернётся, и тест (а с ним и вся
     * Gradle JVM) зависнет навсегда вместо того, чтобы упасть по таймауту. SEPARATE_THREAD
     * запускает тело теста в отдельном потоке и валит тест по дедлайну из потока-наблюдателя,
     * даже если тот, что выполняет тело, остаётся заблокированным (осиротевшим) навсегда.
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void doesNotDeadlockOnLargeOutput() throws Exception {
        DefaultProcessRunner runner = new DefaultProcessRunner(20);
        byte[] payload = new byte[250_000];
        new Random(42).nextBytes(payload);

        byte[] out = runner.run(payload, List.of("sh", "-c", "cat"));

        assertThat(out).isEqualTo(payload);
    }

    // SEPARATE_THREAD по той же причине, что и выше: если регресс в DefaultProcessRunner
    // уберёт реальный таймаут, readAllBytes() на stdout заблокируется навсегда (sleep 60
    // ничего не пишет в stdout), и только отдельный поток-наблюдатель сможет упасть по
    // дедлайну — SAME_THREAD в этом случае зависнет вместе с телом теста.
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
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
