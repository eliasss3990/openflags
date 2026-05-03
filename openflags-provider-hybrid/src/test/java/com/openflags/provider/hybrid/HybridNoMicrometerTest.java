package com.openflags.provider.hybrid;

import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import com.openflags.core.provider.ProviderState;
import com.openflags.provider.file.FileFlagProvider;
import com.openflags.provider.remote.RemoteFlagProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link HybridFlagProvider} works correctly when Micrometer is
 * absent from the classpath. Run under the {@code no-micrometer} Maven profile.
 */
@Tag("no-micrometer")
class HybridNoMicrometerTest {

    static final com.openflags.provider.remote.RemoteProviderConfig REMOTE_CFG =
            new com.openflags.provider.remote.RemoteProviderConfig(
                    URI.create("http://localhost:19999"), "/flags",
                    null, null,
                    Duration.ofSeconds(1), Duration.ofSeconds(2),
                    Duration.ofSeconds(5), Duration.ofSeconds(60),
                    "test-agent");

    @Test
    void micrometerAbsentFromClasspath() {
        assertThatThrownBy(() -> Class.forName(
                "io.micrometer.core.instrument.MeterRegistry",
                false,
                getClass().getClassLoader()))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void hybridProviderWorksWithoutMicrometer(@TempDir Path tempDir) {
        RemoteFlagProvider mockRemote = mock(RemoteFlagProvider.class);
        FileFlagProvider mockFile = mock(FileFlagProvider.class);
        SnapshotWriter mockWriter = mock(SnapshotWriter.class);

        Flag flag = new Flag("x", FlagType.BOOLEAN, FlagValue.of(true, FlagType.BOOLEAN), true, null);
        when(mockRemote.getState()).thenReturn(ProviderState.READY);
        when(mockRemote.getFlag("x")).thenReturn(Optional.of(flag));

        HybridProviderConfig cfg = new HybridProviderConfig(
                REMOTE_CFG, tempDir.resolve("snap.json"), SnapshotFormat.JSON, false,
                Duration.ofMillis(200), false);

        HybridFlagProvider provider = new HybridFlagProvider(cfg, mockRemote, mockFile, mockWriter);
        // No setMetricsRecorder — NOOP must not crash
        provider.init();

        assertThat(provider.getFlag("x")).contains(flag);
        assertThat(provider.getState()).isEqualTo(ProviderState.READY);
        provider.shutdown();
    }
}
