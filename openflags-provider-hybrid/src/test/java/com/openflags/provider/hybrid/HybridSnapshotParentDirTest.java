package com.openflags.provider.hybrid;

import com.openflags.core.exception.ProviderException;
import com.openflags.provider.remote.RemoteProviderConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class HybridSnapshotParentDirTest {

    private static final RemoteProviderConfig REMOTE_CFG = new RemoteProviderConfig(
            URI.create("http://localhost:19999"), "/flags",
            null, null,
            Duration.ofSeconds(1), Duration.ofSeconds(2),
            Duration.ofSeconds(5), Duration.ofSeconds(60),
            "test-agent");

    @TempDir
    Path tempDir;

    @Test
    void init_createsSnapshotParentDirectory_whenMissing() {
        Path nested = tempDir.resolve("a/b/c/snapshot.json");
        HybridFlagProvider provider = HybridFlagProvider.builder()
                .remoteConfig(REMOTE_CFG)
                .snapshotPath(nested)
                .build();

        // init() will fail at remote init (no server), but parent dir must be created first
        try {
            provider.init();
        } catch (ProviderException ignored) {
            // expected: remote unreachable, but that's after parent-dir creation
        } finally {
            provider.shutdown();
        }

        assertThat(Files.isDirectory(nested.getParent())).isTrue();
    }

    @Test
    void init_existingParentDirectory_doesNotThrow() throws Exception {
        Path existing = tempDir.resolve("sub");
        Files.createDirectories(existing);
        Path snapshot = existing.resolve("snapshot.json");

        HybridFlagProvider provider = HybridFlagProvider.builder()
                .remoteConfig(REMOTE_CFG)
                .snapshotPath(snapshot)
                .build();

        try {
            provider.init();
        } catch (ProviderException ignored) {
            // expected: remote unreachable
        } finally {
            provider.shutdown();
        }
    }

    @Test
    void init_snapshotParentIsFile_throwsProviderException() throws Exception {
        // create a file where the parent directory should be
        Path parentAsFile = tempDir.resolve("not-a-dir");
        Files.writeString(parentAsFile, "I am a file");
        Path snapshot = parentAsFile.resolve("snapshot.json");

        HybridFlagProvider provider = HybridFlagProvider.builder()
                .remoteConfig(REMOTE_CFG)
                .snapshotPath(snapshot)
                .build();

        try {
            assertThatExceptionOfType(ProviderException.class)
                    .isThrownBy(provider::init)
                    .withMessageContaining("not a directory")
                    .withMessageContaining(parentAsFile.toString());
        } finally {
            provider.shutdown();
        }
    }
}
