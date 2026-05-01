package com.openflags.core.evaluation;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record describing the outcome of a single flag evaluation.
 *
 * <p>
 * Dispatched to every registered {@link EvaluationListener} after the
 * evaluation pipeline has produced a value. All required fields are
 * validated in the compact constructor; nullable fields are documented
 * per-component.
 *
 * @param flagKey       requested flag key, never {@code null}
 * @param type          requested java type (Boolean.class, String.class, ...)
 * @param defaultValue  caller-supplied default, may be {@code null}
 * @param resolvedValue actual value returned to the caller, may be {@code null}
 * @param reason        outcome classification, never {@code null}
 * @param variant       matched variant identifier when a multi-variant rule
 *                      was selected, otherwise {@code null}
 * @param matchedRuleId identifier of the rule that matched, or {@code null}
 *                      when no rule matched or the engine does not expose it
 * @param context       evaluation context used for resolution, never
 *                      {@code null}
 * @param timestamp     instant the evaluation completed, never {@code null}
 * @param durationNanos wall-clock duration of the evaluation, must be
 *                      {@code >= 0}
 * @param providerType  short label of the provider that resolved the flag
 *                      (e.g. {@code "file"}, {@code "remote"},
 *                      {@code "hybrid"}),
 *                      never {@code null}
 * @since 0.5.0
 */
public record EvaluationEvent(
        String flagKey,
        Class<?> type,
        Object defaultValue,
        Object resolvedValue,
        EvaluationReason reason,
        String variant,
        String matchedRuleId,
        EvaluationContext context,
        Instant timestamp,
        long durationNanos,
        String providerType) {

    public EvaluationEvent {
        Objects.requireNonNull(flagKey, "flagKey must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(providerType, "providerType must not be null");
        if (durationNanos < 0) {
            throw new IllegalArgumentException("durationNanos must be >= 0");
        }
    }
}
