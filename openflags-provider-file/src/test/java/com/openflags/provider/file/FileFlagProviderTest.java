package com.openflags.provider.file;

import com.openflags.core.event.ChangeType;
import com.openflags.core.event.FlagChangeEvent;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.provider.ProviderState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;
import static java.util.concurrent.TimeUnit.*;

class FileFlagProviderTest {

    @Test
    void init_loadsFlags(@TempDir Path tempDir) throws IOException {
        Path file = writeFlags(tempDir, "flags.yml",
                "flags:\n  dark-mode:\n    type: boolean\n    value: true\n");

        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(false).build();
        provider.init();

        assertThat(provider.getState()).isEqualTo(ProviderState.READY);
        assertThat(provider.getFlag("dark-mode")).isPresent();
        assertThat(provider.getFlag("dark-mode").get().type()).isEqualTo(FlagType.BOOLEAN);
        provider.shutdown();
    }

    @Test
    void init_isIdempotent(@TempDir Path tempDir) throws IOException {
        Path file = writeFlags(tempDir, "flags.yml",
                "flags:\n  f:\n    type: boolean\n    value: true\n");

        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(false).build();
        provider.init();
        provider.init();
        assertThat(provider.getState()).isEqualTo(ProviderState.READY);
        provider.shutdown();
    }

    @Test
    void getFlag_returnsEmptyWhenNotFound(@TempDir Path tempDir) throws IOException {
        Path file = writeFlags(tempDir, "flags.yml",
                "flags:\n  known:\n    type: boolean\n    value: true\n");

        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(false).build();
        provider.init();

        assertThat(provider.getFlag("unknown")).isEmpty();
        provider.shutdown();
    }

    @Test
    void getAllFlags_returnsAll(@TempDir Path tempDir) throws IOException {
        Path file = writeFlags(tempDir, "flags.yml",
                "flags:\n  a:\n    type: boolean\n    value: true\n  b:\n    type: string\n    value: x\n");

        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(false).build();
        provider.init();

        assertThat(provider.getAllFlags()).containsKeys("a", "b");
        provider.shutdown();
    }

    @Test
    void hotReload_detectsChange(@TempDir Path tempDir) throws Exception {
        Path file = writeFlags(tempDir, "flags.yml",
                "flags:\n  my-flag:\n    type: boolean\n    value: false\n");

        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(true).build();
        provider.init();
        Thread.sleep(200);

        assertThat(provider.getFlag("my-flag").get().value().asBoolean()).isFalse();

        Files.writeString(file, "flags:\n  my-flag:\n    type: boolean\n    value: true\n");

        await().atMost(5, SECONDS).until(() ->
                provider.getFlag("my-flag")
                        .map(f -> f.value().asBoolean())
                        .orElse(false)
        );

        provider.shutdown();
    }

    @Test
    void hotReload_emitsChangeEvents(@TempDir Path tempDir) throws Exception {
        Path file = writeFlags(tempDir, "flags.yml",
                "flags:\n  toggle:\n    type: boolean\n    value: false\n");

        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(true).build();
        List<FlagChangeEvent> events = new CopyOnWriteArrayList<>();
        provider.addChangeListener(events::add);
        provider.init();
        Thread.sleep(200);

        Files.writeString(file, "flags:\n  toggle:\n    type: boolean\n    value: true\n");

        await().atMost(5, SECONDS).until(() -> !events.isEmpty());

        assertThat(events).anySatisfy(e -> {
            assertThat(e.flagKey()).isEqualTo("toggle");
            assertThat(e.changeType()).isEqualTo(ChangeType.UPDATED);
        });
        provider.shutdown();
    }

    @Test
    void hotReload_keepsOldFlagsOnParseError(@TempDir Path tempDir) throws IOException {
        Path file = writeFlags(tempDir, "flags.yml",
                "flags:\n  stable:\n    type: boolean\n    value: true\n");

        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(true).build();
        provider.init();

        assertThat(provider.getFlag("stable")).isPresent();

        Files.writeString(file, "this is not valid yaml: [\n");

        await().atMost(3, SECONDS).until(() ->
                provider.getState() == ProviderState.ERROR || provider.getFlag("stable").isPresent());

        assertThat(provider.getFlag("stable")).isPresent();
        provider.shutdown();
    }

    @Test
    void shutdown_isIdempotent(@TempDir Path tempDir) throws IOException {
        Path file = writeFlags(tempDir, "flags.yml",
                "flags:\n  f:\n    type: boolean\n    value: true\n");

        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(false).build();
        provider.init();
        provider.shutdown();
        assertThatCode(provider::shutdown).doesNotThrowAnyException();
    }

    @Test
    void evaluationAfterShutdown_throwsIllegalState(@TempDir Path tempDir) throws IOException {
        Path file = writeFlags(tempDir, "flags.yml",
                "flags:\n  f:\n    type: boolean\n    value: true\n");

        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(false).build();
        provider.init();
        provider.shutdown();

        assertThatThrownBy(() -> provider.getFlag("f"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(provider::getAllFlags)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void changeEvents_createdAndDeleted(@TempDir Path tempDir) throws Exception {
        Path file = writeFlags(tempDir, "flags.yml",
                "flags:\n  existing:\n    type: boolean\n    value: true\n");

        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(true).build();
        List<FlagChangeEvent> events = new CopyOnWriteArrayList<>();
        provider.addChangeListener(events::add);
        provider.init();
        Thread.sleep(200);

        Files.writeString(file,
                "flags:\n  new-flag:\n    type: boolean\n    value: true\n");

        await().atMost(5, SECONDS).until(() -> events.size() >= 2);

        assertThat(events).anySatisfy(e -> assertThat(e.changeType()).isEqualTo(ChangeType.DELETED));
        assertThat(events).anySatisfy(e -> assertThat(e.changeType()).isEqualTo(ChangeType.CREATED));
        provider.shutdown();
    }

    @Test
    void shutdownDuringReload_leavesNoInconsistentState(@TempDir Path tempDir) throws Exception {
        Path file = writeFlags(tempDir, "flags.yml",
                "flags:\n  toggle:\n    type: boolean\n    value: false\n");

        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(true).build();
        provider.init();

        AtomicReference<Throwable> listenerError = new AtomicReference<>();
        provider.addChangeListener(e -> {
            try {
                assertThat(provider.getState()).isNotEqualTo(ProviderState.STALE);
            } catch (Throwable t) {
                listenerError.set(t);
            }
        });

        Files.writeString(file, "flags:\n  toggle:\n    type: boolean\n    value: true\n");

        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch shutdownIssued = new CountDownLatch(1);
        pool.submit(() -> {
            provider.shutdown();
            shutdownIssued.countDown();
        });

        assertThat(shutdownIssued.await(3, SECONDS)).isTrue();
        pool.shutdown();

        assertThat(provider.getState()).isEqualTo(ProviderState.STALE);
        assertThat(listenerError.get()).isNull();
        assertThatThrownBy(() -> provider.getFlag("toggle"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getFlag_nullKey_throwsNullPointerException(@TempDir Path tempDir) throws IOException {
        Path file = writeFlags(tempDir, "flags.yml",
                "flags:\n  f:\n    type: boolean\n    value: true\n");
        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(false).build();
        provider.init();

        assertThatThrownBy(() -> provider.getFlag(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("key");
        provider.shutdown();
    }

    private Path writeFlags(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
