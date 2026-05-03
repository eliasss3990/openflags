package com.openflags.provider.hybrid;

import com.openflags.core.metrics.MicrometerMetricsRecorder;
import com.openflags.core.metrics.OpenFlagsMetrics;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import com.openflags.core.provider.ProviderState;
import com.openflags.provider.file.FileFlagProvider;
import com.openflags.provider.remote.RemoteFlagProvider;
import com.openflags.provider.remote.RemotePollListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link HybridFlagProvider} emits the expected Micrometer meters
 * when a {@link MicrometerMetricsRecorder} is wired.
 *
 * <p>Uses {@link SimpleMeterRegistry} so no external infrastructure is required.
 * Each test gets its own fresh registry to avoid cross-test meter pollution.
 */
class HybridFlagProviderMetricsTest {

    static final com.openflags.provider.remote.RemoteProviderConfig REMOTE_CFG =
            new com.openflags.provider.remote.RemoteProviderConfig(
                    URI.create("http://localhost:19999"), "/flags",
                    null, null,
                    Duration.ofSeconds(1), Duration.ofSeconds(2),
                    Duration.ofSeconds(5), Duration.ofSeconds(60),
                    "test-agent");

    @TempDir
    Path tempDir;

    SimpleMeterRegistry registry;
    MicrometerMetricsRecorder recorder;

    RemoteFlagProvider mockRemote;
    FileFlagProvider mockFile;
    SnapshotWriter mockWriter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        recorder = new MicrometerMetricsRecorder(registry, false);

        mockRemote = mock(RemoteFlagProvider.class);
        mockFile = mock(FileFlagProvider.class);
        mockWriter = mock(SnapshotWriter.class);
    }

    private HybridFlagProvider buildProvider() {
        HybridProviderConfig cfg = new HybridProviderConfig(
                REMOTE_CFG, tempDir.resolve("snap.json"), SnapshotFormat.JSON, false,
                Duration.ofMillis(200), false);
        return new HybridFlagProvider(cfg, mockRemote, mockFile, mockWriter);
    }

    // ---- Poll success ----

    @Test
    void pollSuccess_incrementsPollSuccessCounter_andRecordsPollLatency() {
        when(mockRemote.getState()).thenReturn(ProviderState.READY);

        HybridFlagProvider provider = buildProvider();
        provider.setMetricsRecorder(recorder);
        provider.init();

        ArgumentCaptor<RemotePollListener> pollCaptor = ArgumentCaptor.forClass(RemotePollListener.class);
        verify(mockRemote, atLeastOnce()).setPollListener(pollCaptor.capture());
        RemotePollListener listener = pollCaptor.getValue();

        listener.onPollOutcome("success", 1_000_000L);

        Counter success = registry.find(OpenFlagsMetrics.Names.POLL_SUCCESS).counter();
        assertThat(success).isNotNull();
        assertThat(success.count()).isEqualTo(1.0);

        Timer latency = registry.find(OpenFlagsMetrics.Names.POLL_LATENCY)
                .tag(OpenFlagsMetrics.Tags.OUTCOME, "success")
                .timer();
        assertThat(latency).isNotNull();
        assertThat(latency.count()).isEqualTo(1L);

        provider.shutdown();
    }

    // ---- Poll failure ----

    @Test
    void pollFailure_incrementsPollFailureCounter_andActivatesFallback() {
        when(mockRemote.getState()).thenReturn(ProviderState.ERROR);
        when(mockFile.getState()).thenReturn(ProviderState.READY);
        when(mockFile.getFlag(any())).thenReturn(Optional.empty());

        HybridFlagProvider provider = buildProvider();
        provider.setMetricsRecorder(recorder);
        provider.init();

        ArgumentCaptor<RemotePollListener> pollCaptor = ArgumentCaptor.forClass(RemotePollListener.class);
        verify(mockRemote, atLeastOnce()).setPollListener(pollCaptor.capture());
        RemotePollListener listener = pollCaptor.getValue();

        listener.onPollOutcome("failure", 2_000_000L);

        Counter failure = registry.find(OpenFlagsMetrics.Names.POLL_FAILURE).counter();
        assertThat(failure).isNotNull();
        assertThat(failure.count()).isEqualTo(1.0);

        // Trigger routing to file to emit fallback activation
        provider.getFlag("any");

        Counter activations = registry.find(OpenFlagsMetrics.Names.HYBRID_FALLBACK_ACTIVATIONS).counter();
        assertThat(activations).isNotNull();
        assertThat(activations.count()).isEqualTo(1.0);

        Gauge active = registry.find(OpenFlagsMetrics.Names.HYBRID_FALLBACK_ACTIVE).gauge();
        assertThat(active).isNotNull();
        assertThat(active.value()).isEqualTo(1.0);

        provider.shutdown();
    }

    // ---- Fallback activation has bounded cause tag ----

    @Test
    void fallbackActivation_hasCauseTag_primaryError() {
        when(mockRemote.getState()).thenReturn(ProviderState.ERROR);
        when(mockFile.getState()).thenReturn(ProviderState.READY);
        when(mockFile.getFlag(any())).thenReturn(Optional.empty());

        HybridFlagProvider provider = buildProvider();
        provider.setMetricsRecorder(recorder);
        provider.init();

        provider.getFlag("x"); // triggers routing transition to file

        Counter activations = registry.find(OpenFlagsMetrics.Names.HYBRID_FALLBACK_ACTIVATIONS)
                .tag(OpenFlagsMetrics.Tags.CAUSE, "primary_error")
                .counter();
        assertThat(activations).isNotNull();
        assertThat(activations.count()).isEqualTo(1.0);

        provider.shutdown();
    }

    // ---- Primary recovers: deactivation ----

    @Test
    void primaryRecovers_deactivationCounterIncremented_gaugeZero_durationRecorded() {
        // Start with remote ERROR → routing to file
        when(mockRemote.getState()).thenReturn(ProviderState.ERROR);
        when(mockFile.getState()).thenReturn(ProviderState.READY);
        when(mockFile.getFlag(any())).thenReturn(Optional.empty());
        when(mockRemote.getFlag(any())).thenReturn(Optional.empty());

        HybridFlagProvider provider = buildProvider();
        provider.setMetricsRecorder(recorder);
        provider.init();

        provider.getFlag("x"); // activation: remote → file

        // Remote recovers
        when(mockRemote.getState()).thenReturn(ProviderState.READY);

        provider.getFlag("x"); // deactivation: file → remote

        Counter deactivations = registry.find(OpenFlagsMetrics.Names.HYBRID_FALLBACK_DEACTIVATIONS).counter();
        assertThat(deactivations).isNotNull();
        assertThat(deactivations.count()).isEqualTo(1.0);

        Gauge active = registry.find(OpenFlagsMetrics.Names.HYBRID_FALLBACK_ACTIVE).gauge();
        assertThat(active).isNotNull();
        assertThat(active.value()).isEqualTo(0.0);

        Timer duration = registry.find(OpenFlagsMetrics.Names.HYBRID_FALLBACK_DURATION).timer();
        assertThat(duration).isNotNull();
        assertThat(duration.count()).isEqualTo(1L);

        provider.shutdown();
    }

    // ---- State transition ----

    @Test
    void stateTransition_emitsStateTransitionsCounter_withFromToTags() {
        when(mockRemote.getState()).thenReturn(ProviderState.ERROR);
        when(mockFile.getState()).thenReturn(ProviderState.READY);
        when(mockFile.getFlag(any())).thenReturn(Optional.empty());

        HybridFlagProvider provider = buildProvider();
        provider.setMetricsRecorder(recorder);
        provider.init();

        provider.getFlag("x"); // state: NOT_READY → DEGRADED (file ready, remote error)

        Counter transitions = registry.find(OpenFlagsMetrics.Names.HYBRID_STATE_TRANSITIONS)
                .tag(OpenFlagsMetrics.Tags.FROM, "NOT_READY")
                .tag(OpenFlagsMetrics.Tags.TO, "DEGRADED")
                .counter();
        assertThat(transitions).isNotNull();
        assertThat(transitions.count()).isEqualTo(1.0);

        provider.shutdown();
    }

    // ---- Evaluation latency by source ----

    @Test
    void evaluationLatency_primarySource_taggedCorrectly() {
        Flag flag = new Flag("x", FlagType.BOOLEAN, FlagValue.of(true, FlagType.BOOLEAN), true, null);
        when(mockRemote.getState()).thenReturn(ProviderState.READY);
        when(mockRemote.getFlag("x")).thenReturn(Optional.of(flag));

        HybridFlagProvider provider = buildProvider();
        provider.setMetricsRecorder(recorder);
        provider.init();

        provider.getFlag("x");

        Timer latency = registry.find(OpenFlagsMetrics.Names.HYBRID_EVALUATION_LATENCY)
                .tag(OpenFlagsMetrics.Tags.SOURCE, "primary")
                .timer();
        assertThat(latency).isNotNull();
        assertThat(latency.count()).isEqualTo(1L);

        provider.shutdown();
    }

    @Test
    void evaluationLatency_fallbackSource_taggedCorrectly() {
        when(mockRemote.getState()).thenReturn(ProviderState.ERROR);
        when(mockFile.getState()).thenReturn(ProviderState.READY);
        when(mockFile.getFlag("x")).thenReturn(Optional.empty());

        HybridFlagProvider provider = buildProvider();
        provider.setMetricsRecorder(recorder);
        provider.init();

        provider.getFlag("x");

        Timer latency = registry.find(OpenFlagsMetrics.Names.HYBRID_EVALUATION_LATENCY)
                .tag(OpenFlagsMetrics.Tags.SOURCE, "fallback")
                .timer();
        assertThat(latency).isNotNull();
        assertThat(latency.count()).isEqualTo(1L);

        provider.shutdown();
    }

    // ---- State current gauge ----

    @Test
    void stateCurrentGauge_reflectsHybridState() {
        when(mockRemote.getState()).thenReturn(ProviderState.READY);

        HybridFlagProvider provider = buildProvider();
        provider.setMetricsRecorder(recorder);
        provider.init();

        Gauge stateGauge = registry.find(OpenFlagsMetrics.Names.HYBRID_STATE_CURRENT).gauge();
        assertThat(stateGauge).isNotNull();
        // READY = 1 per MicrometerMetricsRecorder.providerStateCode
        assertThat(stateGauge.value()).isEqualTo(1.0);

        provider.shutdown();
    }

    // ---- No metrics recorder: no crash ----

    @Test
    void withoutMetricsRecorder_providerFunctionsNormally() {
        Flag flag = new Flag("y", FlagType.BOOLEAN, FlagValue.of(false, FlagType.BOOLEAN), true, null);
        when(mockRemote.getState()).thenReturn(ProviderState.READY);
        when(mockRemote.getFlag("y")).thenReturn(Optional.of(flag));

        HybridFlagProvider provider = buildProvider();
        // No setMetricsRecorder call — NOOP is the default
        provider.init();

        assertThat(provider.getFlag("y")).contains(flag);
        provider.shutdown();
    }

    // ---- Existing fallback.total metric is still emitted ----

    @Test
    void existingHybridFallbackTotal_stillEmitted() {
        when(mockRemote.getState()).thenReturn(ProviderState.ERROR);
        when(mockFile.getState()).thenReturn(ProviderState.READY);
        when(mockFile.getFlag(any())).thenReturn(Optional.empty());

        HybridFlagProvider provider = buildProvider();
        provider.setMetricsRecorder(recorder);
        provider.init();

        provider.getFlag("x"); // triggers routing transition

        Counter fallbackTotal = registry.find(OpenFlagsMetrics.Names.HYBRID_FALLBACK_TOTAL)
                .tag(OpenFlagsMetrics.Tags.FROM, "remote")
                .tag(OpenFlagsMetrics.Tags.TO, "file")
                .counter();
        assertThat(fallbackTotal).isNotNull();
        assertThat(fallbackTotal.count()).isEqualTo(1.0);

        provider.shutdown();
    }

    // ---- Poll success also increments poll.total (existing metric) ----

    @Test
    void pollSuccess_alsoIncrementsExistingPollTotal() {
        when(mockRemote.getState()).thenReturn(ProviderState.READY);

        HybridFlagProvider provider = buildProvider();
        provider.setMetricsRecorder(recorder);
        provider.init();

        ArgumentCaptor<RemotePollListener> pollCaptor = ArgumentCaptor.forClass(RemotePollListener.class);
        verify(mockRemote, atLeastOnce()).setPollListener(pollCaptor.capture());

        pollCaptor.getValue().onPollOutcome("success", 500_000L);

        Counter pollTotal = registry.find(OpenFlagsMetrics.Names.POLL_TOTAL)
                .tag(OpenFlagsMetrics.Tags.OUTCOME, "success")
                .counter();
        assertThat(pollTotal).isNotNull();
        assertThat(pollTotal.count()).isEqualTo(1.0);

        provider.shutdown();
    }
}
