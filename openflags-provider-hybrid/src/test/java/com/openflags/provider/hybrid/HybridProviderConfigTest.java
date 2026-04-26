package com.openflags.provider.hybrid;

import com.openflags.provider.remote.RemoteProviderConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HybridProviderConfigTest {

    static final RemoteProviderConfig REMOTE_CFG = new RemoteProviderConfig(
            URI.create("http://localhost:8080"), "/flags",
            null, null,
            Duration.ofSeconds(5), Duration.ofSeconds(10),
            Duration.ofSeconds(30), Duration.ofMinutes(5),
            "test-agent");

    @TempDir
    Path tempDir;

    @Test
    void happyPath(@TempDir Path dir) {
        Path snapshot = dir.resolve("snap.json");
        HybridProviderConfig cfg = new HybridProviderConfig(
                REMOTE_CFG, snapshot, SnapshotFormat.JSON, true,
                Duration.ofMillis(500), false);

        assertThat(cfg.remoteConfig()).isSameAs(REMOTE_CFG);
        assertThat(cfg.snapshotPath()).isEqualTo(snapshot);
        assertThat(cfg.snapshotFormat()).isEqualTo(SnapshotFormat.JSON);
        assertThat(cfg.watchSnapshot()).isTrue();
        assertThat(cfg.snapshotDebounce()).isEqualTo(Duration.ofMillis(500));
        assertThat(cfg.failIfNoFallback()).isFalse();
    }

    @Test
    void defaultSnapshotFormat_isApplied(@TempDir Path dir) {
        Path snapshot = dir.resolve("snap.json");
        HybridProviderConfig cfg = new HybridProviderConfig(
                REMOTE_CFG, snapshot, null, true,
                Duration.ofMillis(500), false);
        assertThat(cfg.snapshotFormat()).isEqualTo(SnapshotFormat.JSON);
    }

    @Test
    void defaultDebounce_isApplied(@TempDir Path dir) {
        Path snapshot = dir.resolve("snap.json");
        HybridProviderConfig cfg = new HybridProviderConfig(
                REMOTE_CFG, snapshot, SnapshotFormat.YAML, true,
                null, false);
        assertThat(cfg.snapshotDebounce()).isEqualTo(HybridProviderConfig.DEFAULT_SNAPSHOT_DEBOUNCE);
    }

    @Test
    void nullRemoteConfig_throws(@TempDir Path dir) {
        assertThatNullPointerException()
                .isThrownBy(() -> new HybridProviderConfig(
                        null, dir.resolve("snap.json"), SnapshotFormat.JSON, true,
                        Duration.ofMillis(500), false))
                .withMessageContaining("remoteConfig");
    }

    @Test
    void nullSnapshotPath_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> new HybridProviderConfig(
                        REMOTE_CFG, null, SnapshotFormat.JSON, true,
                        Duration.ofMillis(500), false))
                .withMessageContaining("snapshotPath");
    }

    @Test
    void snapshotPathIsDirectory_throws(@TempDir Path dir) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HybridProviderConfig(
                        REMOTE_CFG, dir, SnapshotFormat.JSON, true,
                        Duration.ofMillis(500), false))
                .withMessageContaining("directory");
    }

    @Test
    void snapshotPathParentDoesNotExist_throws(@TempDir Path dir) {
        Path nonExistentParent = dir.resolve("nonexistent").resolve("snap.json");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HybridProviderConfig(
                        REMOTE_CFG, nonExistentParent, SnapshotFormat.JSON, true,
                        Duration.ofMillis(500), false))
                .withMessageContaining("parent directory");
    }

    @Test
    void debounceZero_throws(@TempDir Path dir) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HybridProviderConfig(
                        REMOTE_CFG, dir.resolve("snap.json"), SnapshotFormat.JSON, true,
                        Duration.ZERO, false))
                .withMessageContaining("positive");
    }

    @Test
    void debounceNegative_throws(@TempDir Path dir) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HybridProviderConfig(
                        REMOTE_CFG, dir.resolve("snap.json"), SnapshotFormat.JSON, true,
                        Duration.ofMillis(-1), false))
                .withMessageContaining("positive");
    }

    @Test
    void debounce_greaterThanHalfPoll_throws(@TempDir Path dir) {
        // pollInterval=30s, debounce=20s → 20s > 15s (half) → throws
        RemoteProviderConfig cfg30 = new RemoteProviderConfig(
                URI.create("http://localhost:8080"), "/flags",
                null, null,
                Duration.ofSeconds(5), Duration.ofSeconds(10),
                Duration.ofSeconds(30), Duration.ofMinutes(5),
                "test-agent");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HybridProviderConfig(
                        cfg30, dir.resolve("snap.json"), SnapshotFormat.JSON, true,
                        Duration.ofSeconds(20), false))
                .withMessageContaining("pollInterval");
    }

    @Test
    void debounce_eqHalfPoll_isAccepted(@TempDir Path dir) {
        // pollInterval=30s, debounce=15s → 15s == 15s (half) → accepted
        RemoteProviderConfig cfg30 = new RemoteProviderConfig(
                URI.create("http://localhost:8080"), "/flags",
                null, null,
                Duration.ofSeconds(5), Duration.ofSeconds(10),
                Duration.ofSeconds(30), Duration.ofMinutes(5),
                "test-agent");
        HybridProviderConfig cfg = new HybridProviderConfig(
                cfg30, dir.resolve("snap.json"), SnapshotFormat.JSON, true,
                Duration.ofSeconds(15), false);
        assertThat(cfg.snapshotDebounce()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void debounce_lessThanHalfPoll_isAccepted(@TempDir Path dir) {
        // pollInterval=30s, debounce=10s → accepted
        RemoteProviderConfig cfg30 = new RemoteProviderConfig(
                URI.create("http://localhost:8080"), "/flags",
                null, null,
                Duration.ofSeconds(5), Duration.ofSeconds(10),
                Duration.ofSeconds(30), Duration.ofMinutes(5),
                "test-agent");
        HybridProviderConfig cfg = new HybridProviderConfig(
                cfg30, dir.resolve("snap.json"), SnapshotFormat.JSON, true,
                Duration.ofSeconds(10), false);
        assertThat(cfg.snapshotDebounce()).isEqualTo(Duration.ofSeconds(10));
    }
}
