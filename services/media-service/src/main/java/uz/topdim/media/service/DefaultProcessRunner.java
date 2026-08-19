package uz.topdim.media.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Запускает внешний процесс (vipsthumbnail): пишет весь stdin и одновременно читает
 * stdout/stderr на отдельных потоках, а вся операция (запись + оба чтения + ожидание
 * завершения процесса) ограничена ОДНИМ общим дедлайном.
 *
 * <p>Почему нельзя писать stdin целиком, а ПОТОМ читать stdout: у ОС-пайпа ограниченный
 * буфер (обычно ~64KB). Если дочерний процесс успевает заполнить этот буфер выводом раньше,
 * чем мы дочитаем весь ввод, он блокируется на записи в stdout; мы в это время блокируемся
 * на записи в его stdin — классический дедлок записи-в-оба-конца. Таймаут на {@code waitFor},
 * выполняемый только после блокирующего чтения/записи, в этой ситуации никогда не сработает,
 * потому что до него управление просто не доходит. Поэтому запись stdin и чтение stdout/stderr
 * идут параллельно на отдельных потоках, а таймаут применяется к операции целиком.
 */
@Component
public class DefaultProcessRunner implements ProcessRunner {

    private static final AtomicInteger THREAD_SEQ = new AtomicInteger();

    private final long timeoutSeconds;

    public DefaultProcessRunner(@Value("${media.vips-timeout-seconds:20}") long timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive: " + timeoutSeconds);
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public byte[] run(byte[] stdin, List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        // Ровно 3 потока — по одному на запись stdin и чтение stdout/stderr. Меньше нельзя:
        // при пуле < 3 обе задачи чтения могут занять все свободные потоки и никогда не
        // дождаться, пока освободится поток для записи stdin (тот же дедлок, только на уровне
        // очереди executor'а вместо ОС-пайпа).
        ExecutorService io = Executors.newFixedThreadPool(3, DefaultProcessRunner::newDaemonThread);
        try {
            Future<byte[]> stdoutFuture = io.submit(process.getInputStream()::readAllBytes);
            Future<byte[]> stderrFuture = io.submit(process.getErrorStream()::readAllBytes);
            Future<?> stdinFuture = io.submit(() -> writeStdin(process, stdin));

            byte[] out;
            byte[] err;
            try {
                stdinFuture.get(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS);
                out = stdoutFuture.get(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS);
                err = stderrFuture.get(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS);
                if (!process.waitFor(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS)) {
                    throw new TimeoutException("process did not exit in time");
                }
            } catch (TimeoutException e) {
                throw new IOException("vips timed out after " + timeoutSeconds + "s", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new IOException("vips I/O failed: " + cause.getMessage(), cause);
            }

            if (process.exitValue() != 0) {
                throw new IOException("vips exit " + process.exitValue() + ": "
                        + new String(err, StandardCharsets.UTF_8));
            }
            return out;
        } finally {
            // Порядок важен. На пути таймаута/ошибки reader-поток (readAllBytes()) может
            // всё ещё быть заблокирован на чтении stdout/stderr в момент, когда мы сюда
            // попадаем. Конкурентный close() потока, который в этот момент читает другой
            // поток, небезопасен в java.io. Поэтому:
            // 1) сначала гарантированно убиваем процесс — это закрывает его файловые
            //    дескрипторы и даёт заблокированному readAllBytes() увидеть EOF и вернуться;
            // 2) shutdownNow() + ограниченный awaitTermination() — дожидаемся, пока
            //    reader/writer-потоки реально завершатся (на успешном пути они уже
            //    завершены, поэтому это быстро);
            // 3) и только теперь закрываем наши концы потоков из этого (главного) потока.
            process.destroyForcibly();
            io.shutdownNow();
            try {
                io.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Не перебрасываем из finally — это заслонило бы основной результат/
                // исключение. Восстанавливаем флаг прерывания и продолжаем закрытие.
                Thread.currentThread().interrupt();
            }
            closeQuietly(process.getInputStream());
            closeQuietly(process.getErrorStream());
        }
    }

    private static void writeStdin(Process process, byte[] data) {
        try (OutputStream os = process.getOutputStream()) {
            os.write(data);
        } catch (IOException e) {
            // Процесс мог завершиться / закрыть stdin раньше, чем мы дописали весь ввод
            // (например, из-за собственной ошибки) — реальная причина будет видна по
            // exit-коду и stderr, поэтому здесь намеренно ничего не бросаем.
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // best-effort очистка
        }
    }

    private static long remainingMillis(long deadlineNanos) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
    }

    private static Thread newDaemonThread(Runnable r) {
        Thread t = new Thread(r, "vips-io-" + THREAD_SEQ.incrementAndGet());
        t.setDaemon(true);
        return t;
    }
}
