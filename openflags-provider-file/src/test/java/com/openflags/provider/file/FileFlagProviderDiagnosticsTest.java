package com.openflags.provider.file;

import com.openflags.core.provider.ProviderDiagnostics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class FileFlagProviderDiagnosticsTest {

    @Test
    void implementsDiagnosticsInterface(@TempDir Path tempDir) throws IOException {
        Path file = writeYaml(tempDir, "flags.yml",
                "flags:\n  a:\n    type: boolean\n    value: true\n");
        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(false).build();
        try {
            assertThat(provider).isInstanceOf(ProviderDiagnostics.class);
        } finally {
            provider.shutdown();
        }
    }

    @Test
    void diagnosticsAreSafeBeforeInit(@TempDir Path tempDir) throws IOException {
        Path file = writeYaml(tempDir, "flags.yml",
                "flags:\n  a:\n    type: boolean\n    value: true\n");
        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(true).build();
        try {
            assertThat(provider.lastUpdate()).isNull();
            assertThat(provider.flagCount()).isZero();
            assertThat(provider.diagnostics())
                    .containsEntry("file.flag_count", 0)
                    .containsEntry("file.watcher_alive", false)
                    .containsEntry("file.last_reload", "");
        } finally {
            provider.shutdown();
        }
    }

    @Test
    void exposesPathFormatLastReloadAndFlagCount(@TempDir Path tempDir) throws IOException {
        Path file = writeYaml(tempDir, "flags.yml",
                "flags:\n  a:\n    type: boolean\n    value: true\n"
                + "  b:\n    type: string\n    value: hi\n");
        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(false).build();
        try {
            provider.init();

            assertThat(provider.providerType()).isEqualTo("file");
            assertThat(provider.flagCount()).isEqualTo(2);
            assertThat(provider.lastUpdate()).isNotNull();

            Map<String, Object> diag = provider.diagnostics();
            assertThat(diag)
                    .containsEntry("file.path", file.toString())
                    .containsEntry("file.format", "yaml")
                    .containsEntry("file.flag_count", 2)
                    .containsEntry("file.watcher_alive", false);
            assertThat(diag.get("file.last_reload"))
                    .asString()
                    .isNotEmpty();
        } finally {
            provider.shutdown();
        }
    }

    @Test
    void lastReloadAdvancesAfterWatcherReload(@TempDir Path tempDir) throws Exception {
        Path file = writeYaml(tempDir, "flags.yml",
                "flags:\n  a:\n    type: boolean\n    value: true\n");
        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(true).build();
        try {
            provider.init();
            Instant initial = provider.lastUpdate();
            assertThat(initial).isNotNull();
            assertThat(provider.diagnostics())
                    .containsEntry("file.watcher_alive", true);

            Thread.sleep(50L);
            Files.writeString(file,
                    "flags:\n  a:\n    type: boolean\n    value: true\n"
                  + "  b:\n    type: string\n    value: hi\n");

            await().atMost(5, SECONDS).until(
                    () -> provider.flagCount() == 2);
            assertThat(provider.lastUpdate()).isAfter(initial);
        } finally {
            provider.shutdown();
        }
    }

    @Test
    void watcherAliveBecomesFalseAfterShutdown(@TempDir Path tempDir) throws IOException {
        Path file = writeYaml(tempDir, "flags.yml",
                "flags:\n  a:\n    type: boolean\n    value: true\n");
        FileFlagProvider provider = FileFlagProvider.builder()
                .path(file).watchEnabled(true).build();
        provider.init();
        assertThat(provider.diagnostics())
                .containsEntry("file.watcher_alive", true);

        provider.shutdown();

        assertThat(provider.diagnostics())
                .containsEntry("file.watcher_alive", false);
    }

    @Test
    void formatDetectedFromExtension(@TempDir Path tempDir) throws IOException {
        Path json = tempDir.resolve("flags.json");
        Files.writeString(json,
                "{\"flags\":{\"a\":{\"type\":\"boolean\",\"value\":true}}}");
        FileFlagProvider provider = FileFlagProvider.builder()
                .path(json).watchEnabled(false).build();
        try {
            provider.init();
            assertThat(provider.diagnostics())
                    .containsEntry("file.format", "json");
        } finally {
            provider.shutdown();
        }
    }

    private Path writeYaml(Path dir, String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p;
    }
}
