package io.github.eliasss3990.openflags.core.metrics;

import io.github.eliasss3990.openflags.core.evaluation.EvaluationEvent;
import io.github.eliasss3990.openflags.core.event.ChangeType;

import java.util.function.Supplier;

/**
 * No-op implementation used when no metrics backend is wired. All methods
 * are intentionally empty so the JIT can inline and elide the calls.
 */
final class NoopMetricsRecorder implements MetricsRecorder {

    static final NoopMetricsRecorder INSTANCE = new NoopMetricsRecorder();

    private NoopMetricsRecorder() {
    }

    @Override
    public void recordEvaluation(EvaluationEvent event) {
    }

    @Override
    public void recordPoll(String outcome, long durationNanos) {
    }

    @Override
    public void recordSnapshotWrite(String outcome, long durationNanos) {
    }

    @Override
    public void recordFlagChange(ChangeType type) {
    }

    @Override
    public void recordHybridFallback(String from, String to) {
    }

    @Override
    public void recordListenerError(String listenerSimpleName) {
    }

    @Override
    public void registerGauge(String name, Iterable<Tag> tags, Supplier<Number> supplier) {
    }
}
