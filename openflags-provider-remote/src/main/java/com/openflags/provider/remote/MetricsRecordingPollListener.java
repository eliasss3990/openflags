package com.openflags.provider.remote;

import com.openflags.core.metrics.MetricsRecorder;
import com.openflags.core.model.Flag;

import java.util.Map;
import java.util.Objects;

/**
 * Bridges {@link RemotePollListener} callbacks into a {@link MetricsRecorder}.
 *
 * <p>
 * Forwards every {@link #onPollOutcome(String, long)} invocation to
 * {@link MetricsRecorder#recordPoll(String, long)}; ignores per-poll snapshots
 * (those are emitted via {@code FlagChangeListener} and counted separately).
 * </p>
 *
 * <p>
 * Wired by the Spring Boot starter when a {@code MetricsRecorder} bean
 * is available. Public so the starter (in a different module) can construct it.
 * </p>
 */
public final class MetricsRecordingPollListener implements RemotePollListener {

    private final MetricsRecorder metrics;

    public MetricsRecordingPollListener(MetricsRecorder metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public void onPollComplete(Map<String, Flag> snapshot) {
        // Snapshot delivery is not a metric concern; counters are driven by onPollOutcome.
    }

    @Override
    public void onPollOutcome(String outcome, long durationNanos) {
        metrics.recordPoll(outcome, durationNanos);
        if ("success".equals(outcome)) {
            metrics.recordHybridPollSuccess(durationNanos);
        } else if ("failure".equals(outcome)) {
            metrics.recordHybridPollFailure(durationNanos);
        }
    }
}
