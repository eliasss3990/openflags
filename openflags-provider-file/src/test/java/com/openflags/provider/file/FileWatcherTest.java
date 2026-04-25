package com.openflags.provider.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

class FileWatcherTest {

    @Test
    void detects_fileChange(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "initial");

        AtomicInteger callCount = new AtomicInteger(0);
        FileWatcher watcher = new FileWatcher(file, callCount::incrementAndGet);
        watcher.start();

        Thread.sleep(200);
        Files.writeString(file, "changed");

        await().atMost(3, TimeUnit.SECONDS).until(() -> callCount.get() > 0);

        watcher.stop();
    }

    @Test
    void debounce_collapseMultipleEvents(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "v0");

        AtomicInteger callCount = new AtomicInteger(0);
        FileWatcher watcher = new FileWatcher(file, callCount::incrementAndGet);
        watcher.start();

        Thread.sleep(200);

        // Write multiple times rapidly to trigger multiple events
        for (int i = 1; i <= 5; i++) {
            Files.writeString(file, "v" + i);
            Thread.sleep(20);
        }

        // Wait for debounce to settle
        Thread.sleep(600);

        // Should have been called fewer times than writes due to debounce
        assertThat(callCount.get()).isLessThan(5);
        watcher.stop();
    }

    @Test
    void stop_isIdempotent(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "content");

        FileWatcher watcher = new FileWatcher(file, () -> {});
        watcher.start();
        Thread.sleep(100);

        assertThatCode(() -> {
            watcher.stop();
            watcher.stop();
        }).doesNotThrowAnyException();
    }

    @Test
    void retries_onCallbackFailure(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "initial");

        AtomicInteger attempts = new AtomicInteger(0);
        CountDownLatch successLatch = new CountDownLatch(1);

        Runnable callback = () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                throw new RuntimeException("Simulated mid-write error");
            }
            successLatch.countDown();
        };

        FileWatcher watcher = new FileWatcher(file, callback);
        watcher.start();

        Thread.sleep(200);
        Files.writeString(file, "changed");

        boolean succeeded = successLatch.await(3, TimeUnit.SECONDS);
        assertThat(succeeded).isTrue();
        assertThat(attempts.get()).isGreaterThanOrEqualTo(2);

        watcher.stop();
    }

    @Test
    void start_calledTwice_doesNotSpawnExtraThreads(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "content");

        FileWatcher watcher = new FileWatcher(file, () -> {});
        watcher.start();

        await().atMost(2, TimeUnit.SECONDS).until(() -> countWatcherThreads() >= 1);
        int countAfterFirstStart = countWatcherThreads();

        watcher.start();
        Thread.sleep(100);

        assertThat(countWatcherThreads()).isEqualTo(countAfterFirstStart);
        watcher.stop();
    }

    @Test
    void start_afterStop_throwsIllegalState(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "content");

        FileWatcher watcher = new FileWatcher(file, () -> {});
        watcher.start();
        Thread.sleep(100);
        watcher.stop();

        assertThatThrownBy(watcher::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stop");
    }

    @Test
    void retryPending_doesNotBlockSecondEvent(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "v0");

        AtomicInteger attempts = new AtomicInteger(0);
        CountDownLatch firstAttemptStarted = new CountDownLatch(1);
        CountDownLatch secondCallReceived = new CountDownLatch(1);

        Runnable callback = () -> {
            int n = attempts.incrementAndGet();
            if (n == 1) {
                firstAttemptStarted.countDown();
                throw new RuntimeException("Simulated mid-write failure");
            }
            secondCallReceived.countDown();
        };

        FileWatcher watcher = new FileWatcher(file, callback);
        watcher.start();

        Thread.sleep(200);
        Files.writeString(file, "v1");

        assertThat(firstAttemptStarted.await(3, TimeUnit.SECONDS)).isTrue();

        Files.writeString(file, "v2");

        assertThat(secondCallReceived.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(attempts.get()).isGreaterThanOrEqualTo(2);

        watcher.stop();
    }

    private static int countWatcherThreads() {
        int count = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().equals("openflags-filewatcher")) {
                count++;
            }
        }
        return count;
    }
}
