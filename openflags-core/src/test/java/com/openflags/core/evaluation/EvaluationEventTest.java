package com.openflags.core.evaluation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationEventTest {

    private static EvaluationEvent newEvent(String flagKey,
            Class<?> type,
            EvaluationReason reason,
            EvaluationContext ctx,
            Instant ts,
            long durationNanos,
            String providerType) {
        return new EvaluationEvent(
                flagKey, type, true, true, reason, null, null, ctx, ts, durationNanos, providerType);
    }

    @Test
    void valid_construction_succeeds() {
        EvaluationEvent e = new EvaluationEvent(
                "flag",
                Boolean.class,
                false,
                true,
                EvaluationReason.RESOLVED,
                "v1",
                "rule-7",
                EvaluationContext.empty(),
                Instant.now(),
                123L,
                "file");
        assertThat(e.flagKey()).isEqualTo("flag");
        assertThat(e.variant()).isEqualTo("v1");
        assertThat(e.matchedRuleId()).isEqualTo("rule-7");
        assertThat(e.durationNanos()).isEqualTo(123L);
    }

    @Test
    void nullable_fields_accept_null() {
        EvaluationEvent e = new EvaluationEvent(
                "flag",
                Map.class,
                null,
                null,
                EvaluationReason.RESOLVED,
                null,
                null,
                EvaluationContext.empty(),
                Instant.now(),
                0L,
                "remote");
        assertThat(e.defaultValue()).isNull();
        assertThat(e.resolvedValue()).isNull();
        assertThat(e.variant()).isNull();
        assertThat(e.matchedRuleId()).isNull();
    }

    @Test
    void nullFlagKey_throws() {
        assertThatThrownBy(() -> newEvent(null, Boolean.class, EvaluationReason.RESOLVED,
                EvaluationContext.empty(), Instant.now(), 0L, "file"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("flagKey");
    }

    @Test
    void nullType_throws() {
        assertThatThrownBy(() -> newEvent("k", null, EvaluationReason.RESOLVED,
                EvaluationContext.empty(), Instant.now(), 0L, "file"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type");
    }

    @Test
    void nullReason_throws() {
        assertThatThrownBy(() -> newEvent("k", Boolean.class, null,
                EvaluationContext.empty(), Instant.now(), 0L, "file"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void nullContext_throws() {
        assertThatThrownBy(() -> newEvent("k", Boolean.class, EvaluationReason.RESOLVED,
                null, Instant.now(), 0L, "file"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context");
    }

    @Test
    void nullTimestamp_throws() {
        assertThatThrownBy(() -> newEvent("k", Boolean.class, EvaluationReason.RESOLVED,
                EvaluationContext.empty(), null, 0L, "file"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void nullProviderType_throws() {
        assertThatThrownBy(() -> newEvent("k", Boolean.class, EvaluationReason.RESOLVED,
                EvaluationContext.empty(), Instant.now(), 0L, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("providerType");
    }

    @Test
    void negativeDuration_throws() {
        assertThatThrownBy(() -> newEvent("k", Boolean.class, EvaluationReason.RESOLVED,
                EvaluationContext.empty(), Instant.now(), -1L, "file"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("durationNanos");
    }
}
