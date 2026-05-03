package com.openflags.core.metrics;

import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationEvent;
import com.openflags.core.evaluation.EvaluationReason;
import com.openflags.core.event.ChangeType;
import com.openflags.core.provider.ProviderState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("deprecation") // ProviderState.STALE referenced in providerStateCode test (ADR-6)
class MicrometerMetricsRecorderTest {

        private SimpleMeterRegistry registry;
        private MicrometerMetricsRecorder recorder;

        @BeforeEach
        void setUp() {
                registry = new SimpleMeterRegistry();
                recorder = new MicrometerMetricsRecorder(registry, true);
        }

        private static EvaluationEvent eval(String key, Class<?> type, EvaluationReason reason,
                        String variant, long durationNanos, String providerType) {
                return new EvaluationEvent(key, type, false, true, reason, variant, null,
                                EvaluationContext.empty(), Instant.now(), durationNanos, providerType);
        }

        @Test
        void recordEvaluation_emitsCounterAndTimer_withTags() {
                recorder.recordEvaluation(
                                eval("dark-mode", Boolean.class, EvaluationReason.RESOLVED, null, 1_000_000L, "file"));
                recorder.recordEvaluation(
                                eval("dark-mode", Boolean.class, EvaluationReason.RESOLVED, null, 1_000_000L, "file"));

                Counter c = registry.find("openflags.evaluations.total")
                                .tag("flag", "dark-mode")
                                .tag("type", "boolean")
                                .tag("reason", "RESOLVED")
                                .tag("provider.type", "file")
                                .counter();
                assertThat(c).isNotNull();
                assertThat(c.count()).isEqualTo(2.0);

                assertThat(registry.find("openflags.evaluation.duration").timer()).isNotNull();
        }

        @Test
        void tagFlagKeyFalse_omitsFlagAndVariantTags() {
                MicrometerMetricsRecorder noFlag = new MicrometerMetricsRecorder(registry, false);
                noFlag.recordEvaluation(
                                eval("dark-mode", String.class, EvaluationReason.VARIANT, "blue", 0L, "remote"));

                Counter c = registry.find("openflags.evaluations.total")
                                .tag("type", "string").tag("reason", "VARIANT").counter();
                assertThat(c).isNotNull();
                assertThat(c.getId().getTag("flag")).isNull();
                assertThat(c.getId().getTag("variant")).isNull();
        }

        @Test
        void errorReasons_emitErrorCounter_withErrorTypeTag() {
                recorder.recordEvaluation(
                                eval("missing", Boolean.class, EvaluationReason.FLAG_NOT_FOUND, null, 0L, "file"));
                recorder.recordEvaluation(
                                eval("typed", Boolean.class, EvaluationReason.TYPE_MISMATCH, null, 0L, "file"));
                recorder.recordEvaluation(
                                eval("provFail", Boolean.class, EvaluationReason.PROVIDER_ERROR, null, 0L, "remote"));

                assertThat(registry.find("openflags.evaluations.errors.total").tag("error.type", "MISSING").counter()
                                .count())
                                .isEqualTo(1.0);
                assertThat(registry.find("openflags.evaluations.errors.total").tag("error.type", "TYPE_MISMATCH")
                                .counter()
                                .count()).isEqualTo(1.0);
                assertThat(
                                registry.find("openflags.evaluations.errors.total").tag("error.type", "PROVIDER_ERROR")
                                                .counter().count())
                                .isEqualTo(1.0);
        }

        @Test
        void nonErrorReasons_doNotEmitErrorCounter() {
                recorder.recordEvaluation(eval("ok", Boolean.class, EvaluationReason.RESOLVED, null, 0L, "file"));
                assertThat(registry.find("openflags.evaluations.errors.total").counters()).isEmpty();
        }

        @Test
        void registerGauge_returnsCurrentSupplierValue() {
                AtomicInteger value = new AtomicInteger(7);
                recorder.registerGauge("openflags.test.gauge", List.of(new Tag("k", "v")), value::get);

                Gauge g = registry.find("openflags.test.gauge").tag("k", "v").gauge();
                assertThat(g).isNotNull();
                assertThat(g.value()).isEqualTo(7.0);

                value.set(42);
                assertThat(g.value()).isEqualTo(42.0);
        }

        @Test
        void normalizeVariant_shortInputs_passThrough() {
                assertThat(recorder.normalizeVariant("v1")).isEqualTo("v1");
                assertThat(recorder.normalizeVariant("a".repeat(64))).isEqualTo("a".repeat(64));
        }

        @Test
        void normalizeVariant_longInputsWithSamePrefix_produceDifferentTags() {
                String prefix = "x".repeat(70);
                String a = prefix + "-alpha";
                String b = prefix + "-beta";
                String na = recorder.normalizeVariant(a);
                String nb = recorder.normalizeVariant(b);

                assertThat(na).startsWith("x".repeat(56) + "~").hasSize(56 + 1 + 7);
                assertThat(nb).startsWith("x".repeat(56) + "~").hasSize(56 + 1 + 7);
                assertThat(na).isNotEqualTo(nb);
        }

        @Test
        void providerStateCode_isStableNonOrdinalMapping() {
                Map<ProviderState, Integer> expected = Map.of(
                                ProviderState.NOT_READY, 0,
                                ProviderState.READY, 1,
                                ProviderState.DEGRADED, 2,
                                ProviderState.ERROR, 3,
                                ProviderState.STALE, 4,
                                ProviderState.SHUTDOWN, 5);
                for (Map.Entry<ProviderState, Integer> e : expected.entrySet()) {
                        assertThat(MicrometerMetricsRecorder.providerStateCode(e.getKey()))
                                        .as("code for %s", e.getKey()).isEqualTo(e.getValue());
                }
                assertThat(MicrometerMetricsRecorder.providerStateCode(null)).isEqualTo(-1);
        }

        @Test
        void typeTag_mapsKnownTypes() {
                assertThat(MicrometerMetricsRecorder.typeTag(Boolean.class)).isEqualTo("boolean");
                assertThat(MicrometerMetricsRecorder.typeTag(String.class)).isEqualTo("string");
                assertThat(MicrometerMetricsRecorder.typeTag(Double.class)).isEqualTo("number");
                assertThat(MicrometerMetricsRecorder.typeTag(Long.class)).isEqualTo("number");
                assertThat(MicrometerMetricsRecorder.typeTag(Map.class)).isEqualTo("object");
        }

        @Test
        void poll_snapshotWrite_flagChange_hybridFallback_listenerError() {
                recorder.recordPoll("success", 5_000_000L);
                recorder.recordPoll("failure", 1_000_000L);
                recorder.recordSnapshotWrite("success", 2_000_000L);
                recorder.recordFlagChange(ChangeType.UPDATED);
                recorder.recordHybridFallback("remote", "file");
                recorder.recordListenerError("AuditListener");

                assertThat(registry.find("openflags.poll.total").tag("outcome", "success").counter().count())
                                .isEqualTo(1.0);
                assertThat(registry.find("openflags.poll.total").tag("outcome", "failure").counter().count())
                                .isEqualTo(1.0);
                assertThat(registry.find("openflags.poll.duration").tag("outcome", "success").timer().count())
                                .isEqualTo(1L);
                assertThat(registry.find("openflags.poll.duration").tag("outcome", "success").timer()
                                .totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isEqualTo(5_000_000.0);
                assertThat(registry.find("openflags.snapshot.writes.total").tag("outcome", "success").counter().count())
                                .isEqualTo(1.0);
                assertThat(registry.find("openflags.snapshot.write.duration").tag("outcome", "success").timer().count())
                                .isEqualTo(1L);
                assertThat(registry.find("openflags.snapshot.write.duration").tag("outcome", "success").timer()
                                .totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isEqualTo(2_000_000.0);
                assertThat(registry.find("openflags.flag_changes.total").tag("change_type", "UPDATED").counter()
                                .count())
                                .isEqualTo(1.0);
                assertThat(registry.find("openflags.hybrid.fallback.total").tag("from", "remote").tag("to", "file")
                                .counter()
                                .count()).isEqualTo(1.0);
                assertThat(registry.find("openflags.evaluations.listener.errors.total").tag("listener", "AuditListener")
                                .counter().count()).isEqualTo(1.0);
        }

        @Test
        void publicMethods_rejectNullArguments() {
                assertThatThrownBy(() -> recorder.recordEvaluation(null))
                                .isInstanceOf(NullPointerException.class).hasMessageContaining("event");
                assertThatThrownBy(() -> recorder.recordPoll(null, 0L))
                                .isInstanceOf(NullPointerException.class).hasMessageContaining("outcome");
                assertThatThrownBy(() -> recorder.recordSnapshotWrite(null, 0L))
                                .isInstanceOf(NullPointerException.class).hasMessageContaining("outcome");
                assertThatThrownBy(() -> recorder.recordFlagChange(null))
                                .isInstanceOf(NullPointerException.class).hasMessageContaining("type");
                assertThatThrownBy(() -> recorder.recordHybridFallback(null, "file"))
                                .isInstanceOf(NullPointerException.class).hasMessageContaining("from");
                assertThatThrownBy(() -> recorder.recordHybridFallback("remote", null))
                                .isInstanceOf(NullPointerException.class).hasMessageContaining("to");
                assertThatThrownBy(() -> recorder.recordListenerError(null))
                                .isInstanceOf(NullPointerException.class).hasMessageContaining("listenerSimpleName");
                assertThatThrownBy(() -> recorder.registerGauge(null, List.of(), () -> 1))
                                .isInstanceOf(NullPointerException.class).hasMessageContaining("name");
                assertThatThrownBy(() -> recorder.registerGauge("g", null, () -> 1))
                                .isInstanceOf(NullPointerException.class).hasMessageContaining("tags");
                assertThatThrownBy(() -> recorder.registerGauge("g", List.of(), null))
                                .isInstanceOf(NullPointerException.class).hasMessageContaining("supplier");
        }

        @Test
        void counterCache_reusesInstancesForSameTags() {
                recorder.recordEvaluation(eval("k", Boolean.class, EvaluationReason.RESOLVED, null, 0L, "file"));
                recorder.recordEvaluation(eval("k", Boolean.class, EvaluationReason.RESOLVED, null, 0L, "file"));

                long count = registry.find("openflags.evaluations.total")
                                .tag("flag", "k").counters().size();
                assertThat(count).isEqualTo(1L);
        }
}
