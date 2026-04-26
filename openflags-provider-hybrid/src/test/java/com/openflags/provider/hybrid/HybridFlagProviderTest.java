package com.openflags.provider.hybrid;

import com.openflags.core.event.ChangeType;
import com.openflags.core.event.FlagChangeEvent;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderState;
import com.openflags.provider.file.FileFlagProvider;
import com.openflags.provider.remote.RemoteFlagProvider;
import com.openflags.provider.remote.RemoteProviderConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for HybridFlagProvider using real file+remote providers created via a test helper.
 * We use a subclass trick to inject mock providers.
 */
class HybridFlagProviderTest {

    static final RemoteProviderConfig REMOTE_CFG = new RemoteProviderConfig(
            URI.create("http://localhost:19999"), "/flags",
            null, null,
            Duration.ofSeconds(1), Duration.ofSeconds(2),
            Duration.ofSeconds(5), Duration.ofSeconds(60),
            "test-agent");

    @TempDir
    Path tempDir;

    // ---- Builder tests ----

    @Test
    void builder_missingRemoteConfig_throws(@TempDir Path dir) {
        assertThatThrownBy(() -> HybridFlagProvider.builder()
                .snapshotPath(dir.resolve("snap.json"))
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("remoteConfig");
    }

    @Test
    void builder_missingSnapshotPath_throws() {
        assertThatThrownBy(() -> HybridFlagProvider.builder()
                .remoteConfig(REMOTE_CFG)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("snapshotPath");
    }

    @Test
    void builder_defaults_applied(@TempDir Path dir) {
        HybridFlagProvider provider = HybridFlagProvider.builder()
                .remoteConfig(REMOTE_CFG)
                .snapshotPath(dir.resolve("snap.json"))
                .build();
        assertThat(provider).isNotNull();
    }

    @Test
    void builder_allSetters(@TempDir Path dir) {
        HybridFlagProvider provider = HybridFlagProvider.builder()
                .remoteConfig(REMOTE_CFG)
                .snapshotPath(dir.resolve("snap.json").toString())
                .snapshotFormat(SnapshotFormat.YAML)
                .watchSnapshot(false)
                .snapshotDebounce(Duration.ofMillis(200))
                .failIfNoFallback(true)
                .build();
        assertThat(provider).isNotNull();
    }

    // ---- getFlag before init ----

    @Test
    void getFlag_beforeInit_throwsIllegalState(@TempDir Path dir) {
        HybridFlagProvider provider = HybridFlagProvider.builder()
                .remoteConfig(REMOTE_CFG)
                .snapshotPath(dir.resolve("snap.json"))
                .build();
        assertThatIllegalStateException()
                .isThrownBy(() -> provider.getFlag("some-flag"))
                .withMessageContaining("not been initialized");
    }

    // ---- shutdown idempotent ----

    @Test
    void shutdown_isIdempotent(@TempDir Path dir) {
        HybridFlagProvider provider = HybridFlagProvider.builder()
                .remoteConfig(REMOTE_CFG)
                .snapshotPath(dir.resolve("snap.json"))
                .build();
        provider.shutdown();
        provider.shutdown(); // should not throw
        assertThat(provider.getState()).isEqualTo(ProviderState.SHUTDOWN);
    }

    // ---- getState routing ----

    @Test
    void getState_initialIsNotReady(@TempDir Path dir) {
        HybridFlagProvider provider = HybridFlagProvider.builder()
                .remoteConfig(REMOTE_CFG)
                .snapshotPath(dir.resolve("snap.json"))
                .build();
        assertThat(provider.getState()).isEqualTo(ProviderState.NOT_READY);
    }

    // ---- C1: failIfNoFallback ----

    @Test
    void init_bothProvidersFail_failIfNoFallbackTrue_throws(@TempDir Path dir) {
        HybridFlagProvider provider = HybridFlagProvider.builder()
                .remoteConfig(REMOTE_CFG)
                .snapshotPath(dir.resolve("snap.json")) // file does not exist
                .failIfNoFallback(true)
                .build();
        assertThatThrownBy(provider::init)
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("both remote and file providers failed");
    }

    @Test
    void init_bothProvidersFail_failIfNoFallbackFalse_startsInErrorState(@TempDir Path dir) {
        HybridFlagProvider provider = HybridFlagProvider.builder()
                .remoteConfig(REMOTE_CFG)
                .snapshotPath(dir.resolve("snap.json")) // file does not exist
                .failIfNoFallback(false)
                .build();
        // must not throw
        provider.init();
        assertThat(provider.getState()).isEqualTo(ProviderState.ERROR);
        assertThat(provider.getFlag("any-flag")).isEmpty();
        provider.shutdown();
    }

    // ---- I1: init() guard order ----

    @Test
    void init_afterShutdown_throws(@TempDir Path dir) {
        HybridFlagProvider provider = HybridFlagProvider.builder()
                .remoteConfig(REMOTE_CFG)
                .snapshotPath(dir.resolve("snap.json"))
                .build();
        provider.shutdown();
        assertThatIllegalStateException()
                .isThrownBy(provider::init)
                .withMessageContaining("has been shut down");
    }

    @Test
    void init_calledTwice_isNoop(@TempDir Path dir) {
        RemoteFlagProvider mockRemote = mock(RemoteFlagProvider.class);
        when(mockRemote.getState()).thenReturn(ProviderState.READY);
        FileFlagProvider mockFile = mock(FileFlagProvider.class);
        when(mockFile.getState()).thenReturn(ProviderState.READY);
        SnapshotWriter mockWriter = mock(SnapshotWriter.class);

        HybridProviderConfig cfg = new HybridProviderConfig(
                REMOTE_CFG, dir.resolve("snap.json"), SnapshotFormat.JSON, false,
                Duration.ofMillis(200), false);

        HybridFlagProvider provider = new HybridFlagProvider(cfg, mockRemote, mockFile, mockWriter);
        provider.init();
        provider.init(); // second call should be no-op

        // verify init() was called exactly once on each sub-provider
        verify(mockRemote, org.mockito.Mockito.times(1)).init();
        verify(mockFile, org.mockito.Mockito.times(1)).init();
    }
}
