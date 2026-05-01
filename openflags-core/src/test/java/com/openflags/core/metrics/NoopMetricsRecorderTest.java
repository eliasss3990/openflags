package com.openflags.core.metrics;

import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationEvent;
import com.openflags.core.evaluation.EvaluationReason;
import com.openflags.core.event.ChangeType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NoopMetricsRecorderTest {

    @Test
    void noopSingleton_isExposedViaInterface() {
        assertThat(MetricsRecorder.NOOP)
                .isNotNull()
                .isInstanceOf(NoopMetricsRecorder.class)
                .isSameAs(NoopMetricsRecorder.INSTANCE);
    }

    @Test
    void everyMethod_doesNotThrow() {
        MetricsRecorder noop = MetricsRecorder.NOOP;

        EvaluationEvent event = new EvaluationEvent(
                "k", Boolean.class, false, true, EvaluationReason.RESOLVED,
                null, null, EvaluationContext.empty(), Instant.now(), 0L, "file");

        assertThatCode(() -> {
            noop.recordEvaluation(event);
            noop.recordPoll("success", 1_000_000L);
            noop.recordSnapshotWrite("success", 0L);
            noop.recordFlagChange(ChangeType.UPDATED);
            noop.recordHybridFallback("remote", "file");
            noop.recordListenerError("MyListener");
            noop.registerGauge("name", List.of(new Tag("k", "v")), () -> 1);
        }).doesNotThrowAnyException();
    }
}
