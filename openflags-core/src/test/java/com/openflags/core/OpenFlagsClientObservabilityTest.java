package com.openflags.core;

import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationEvent;
import com.openflags.core.evaluation.EvaluationListener;
import com.openflags.core.metrics.MetricsRecorder;
import com.openflags.core.metrics.Tag;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import com.openflags.core.provider.FlagProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenFlagsClientObservabilityTest {

    @Mock
    private FlagProvider provider;

    private RecordingMetrics metrics;
    private OpenFlagsClient client;

    @BeforeEach
    void setUp() {
        doNothing().when(provider).init();
        metrics = new RecordingMetrics();
    }

    @AfterEach
    void tearDown() {
        if (client != null)
            client.shutdown();
    }

    private OpenFlagsClient buildWith(EvaluationListener... ls) {
        OpenFlagsClientBuilder b = OpenFlagsClient.builder()
                .provider(provider)
                .providerType("file")
                .metricsRecorder(metrics);
        for (EvaluationListener l : ls)
            b.addEvaluationListener(l);
        return b.build();
    }

    private void mockBoolean(String key, boolean value) {
        Flag flag = new Flag(key, FlagType.BOOLEAN, FlagValue.of(value, FlagType.BOOLEAN), true, null);
        when(provider.getFlag(key)).thenReturn(Optional.of(flag));
    }

    @Test
    void listener_receivesEventForEachEvaluation() {
        List<EvaluationEvent> events = new ArrayList<>();
        client = buildWith(events::add);
        mockBoolean("k", true);

        client.getBooleanValue("k", false);
        client.getBooleanValue("k", false);
        client.getBooleanValue("k", false);

        assertThat(events).hasSize(3);
        assertThat(events.get(0).flagKey()).isEqualTo("k");
        assertThat(events.get(0).providerType()).isEqualTo("file");
        assertThat(events.get(0).type()).isEqualTo(Boolean.class);
        assertThat(events.get(0).durationNanos()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void metrics_recordEvaluation_calledPerEvaluation() {
        client = buildWith();
        mockBoolean("k", true);
        client.getBooleanValue("k", false);
        client.getBooleanValue("k", false);

        assertThat(metrics.evaluations).hasSize(2);
    }

    @Test
    void faultyListener_doesNotBreakEvaluation_andIncrementsErrorCounter() {
        EvaluationListener bad = e -> {
            throw new RuntimeException("boom");
        };
        AtomicInteger calls = new AtomicInteger();
        EvaluationListener good = e -> calls.incrementAndGet();
        client = buildWith(bad, good);
        mockBoolean("k", true);

        boolean result = client.getBooleanValue("k", false);

        assertThat(result).isTrue();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(metrics.listenerErrors).hasSize(1);
        assertThat(metrics.listenerErrors.get(0)).isNotEmpty();
    }

    @Test
    void reentrantListener_doesNotDeadlockNorRecurseInfinitely() {
        AtomicInteger depth = new AtomicInteger();
        AtomicInteger seen = new AtomicInteger();
        EvaluationListener reentrant = event -> {
            seen.incrementAndGet();
            if (depth.incrementAndGet() == 1 && event.flagKey().equals("outer")) {
                client.getBooleanValue("inner", false);
            }
            depth.decrementAndGet();
        };
        client = buildWith(reentrant);
        mockBoolean("outer", true);
        mockBoolean("inner", true);

        client.getBooleanValue("outer", false);

        assertThat(seen.get()).isEqualTo(2);
    }

    @Test
    void noopMetrics_byDefault() {
        client = OpenFlagsClient.builder().provider(provider).build();
        mockBoolean("k", true);
        // does not throw, returns the value
        assertThat(client.getBooleanValue("k", false)).isTrue();
    }

    @Test
    void removeEvaluationListener_returnsTrueWhenRegistered() {
        EvaluationListener l = e -> {
        };
        client = buildWith(l);
        assertThat(client.removeEvaluationListener(l)).isTrue();
        assertThat(client.removeEvaluationListener(l)).isFalse();
    }

    @Test
    void addEvaluationListener_nullThrowsNpe() {
        client = buildWith();
        assertThatNullPointerException().isThrownBy(() -> client.addEvaluationListener(null));
    }

    static final class RecordingMetrics implements MetricsRecorder {
        final List<EvaluationEvent> evaluations = new ArrayList<>();
        final List<String> listenerErrors = new ArrayList<>();

        @Override
        public void recordEvaluation(EvaluationEvent event) {
            evaluations.add(event);
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
        public void registerGauge(String name, Iterable<Tag> tags, Supplier<Number> supplier) {
        }
    }
}
