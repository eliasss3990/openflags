package com.openflags.provider.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class FileWatcherDebounceConfigTest {

    @Test
    void defaultDebounceIsTwoHundredMillis() {
        assertThat(FileWatcher.DEFAULT_DEBOUNCE).isEqualTo(Duration.ofMillis(200));
    }

    @Test
    void customDebounceConstructorAccepted(@TempDir Path tempDir) {
        Path file = tempDir.resolve("flags.yml");
        // Verifies a strictly positive Duration is accepted by the constructor.
        // We do not start the watcher: this test asserts only that construction
        // does not throw for a valid debounce value.
        assertThatNoException().isThrownBy(
                () -> new FileWatcher(file, () -> {}, Duration.ofMillis(50)));
    }

    @Test
    void zeroDebounceRejected(@TempDir Path tempDir) {
        Path file = tempDir.resolve("flags.yml");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FileWatcher(file, () -> {
                }, Duration.ZERO));
    }

    @Test
    void negativeDebounceRejected(@TempDir Path tempDir) {
        Path file = tempDir.resolve("flags.yml");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FileWatcher(file, () -> {
                }, Duration.ofMillis(-1)));
    }

    @Test
    void nullDebounceRejected(@TempDir Path tempDir) {
        Path file = tempDir.resolve("flags.yml");
        assertThatNullPointerException()
                .isThrownBy(() -> new FileWatcher(file, () -> {
                }, null));
    }
}
