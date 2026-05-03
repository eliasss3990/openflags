package com.openflags.core;

import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationEvent;
import com.openflags.core.evaluation.EvaluationListener;
import com.openflags.core.evaluation.EvaluationReason;
import com.openflags.core.metrics.MetricsRecorder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationListenerRegistryTest {

    private static EvaluationEvent sampleEvent() {
        return new EvaluationEvent(
                "flag-x", Boolean.class, false, true,
                EvaluationReason.RESOLVED, null, null,
                EvaluationContext.empty(), Instant.now(), 100L, "test");
    }

    private static final class CountingRecorder implements MetricsRecorder {
        final List<String> listenerErrors = new ArrayList<>();

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
        public void recordFlagChange(com.openflags.core.event.ChangeType type) {
        }

        @Override
        public void recordHybridFallback(String from, String to) {
        }

        @Override
        public void recordListenerError(String listenerSimpleName) {
            listenerErrors.add(listenerSimpleName);
        }

        @Override
        public void registerGauge(String name, Iterable<com.openflags.core.metrics.Tag> tags,
                java.util.function.Supplier<Number> supplier) {
        }
    }

    @Test
    void distinctInstancesOfSameClass_haveIndependentCounters() {
        CountingRecorder recorder = new CountingRecorder();
        EvaluationListenerRegistry registry = new EvaluationListenerRegistry(recorder);

        EvaluationListener l1 = e -> {
            throw new RuntimeException("boom");
        };
        EvaluationListener l2 = e -> {
            throw new RuntimeException("boom");
        };
        registry.add(l1);
        registry.add(l2);

        // Each listener fails once; registry must dispatch to both regardless of
        // the other's failure, and per-instance counters must increment independently.
        registry.dispatch(sampleEvent());

        assertThat(recorder.listenerErrors).hasSize(2);
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    void remove_clearsFailureCounter_preventingLeak() {
        CountingRecorder recorder = new CountingRecorder();
        EvaluationListenerRegistry registry = new EvaluationListenerRegistry(recorder);

        EvaluationListener failing = e -> {
            throw new RuntimeException("boom");
        };
        registry.add(failing);
        registry.dispatch(sampleEvent());

        assertThat(registry.remove(failing)).isTrue();
        assertThat(registry.size()).isZero();
        // Re-adding triggers a fresh counter; we verify behavioral cleanliness via
        // dispatching again — the WARN rate-limit (powers of two) hits "1" again.
        registry.add(failing);
        registry.dispatch(sampleEvent());
        assertThat(recorder.listenerErrors).hasSize(2);
    }

    @Test
    void remove_returnsFalseForUnknownListener() {
        EvaluationListenerRegistry registry = new EvaluationListenerRegistry(MetricsRecorder.NOOP);
        EvaluationListener listener = e -> {
        };
        assertThat(registry.remove(listener)).isFalse();
    }

    @Test
    void remove_nullTolerated() {
        EvaluationListenerRegistry registry = new EvaluationListenerRegistry(MetricsRecorder.NOOP);
        assertThat(registry.remove(null)).isFalse();
    }
}
