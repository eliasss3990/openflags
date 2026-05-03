package com.openflags.core.evaluation;

import java.util.Objects;

/**
 * The result of evaluating a feature flag.
 * <p>
 * Contains the resolved value, the reason why that value was chosen, the flag key,
 * and — when a {@code MultiVariantRule} matched — the variant label and the matched
 * rule identifier. Implemented as a Java record (ADR-009).
 * </p>
 *
 * <h2>Nullability of {@code value}</h2>
 * <p>
 * {@code value} is {@code null} only when the caller passed {@code null} as the
 * default to a method that accepts a nullable default (e.g.
 * {@link com.openflags.core.OpenFlagsClient#getStringResult(String, String, EvaluationContext)
 * getStringResult} or
 * {@link com.openflags.core.OpenFlagsClient#getObjectResult(String, java.util.Map, EvaluationContext)
 * getObjectResult}) and the flag could not be resolved (missing, disabled,
 * type mismatch, or evaluation error).
 * </p>
 * <p>
 * When the caller's default is non-null, {@code value} is always non-null.
 * The boolean and number variants use primitive defaults so {@code value()}
 * is auto-boxed but never returns {@code null}.
 * </p>
 *
 * @param <T>           the type of the resolved value
 * @param value         the resolved value, or the caller's default. May be {@code null}
 *                      only when the caller's default is {@code null} and the flag could
 *                      not be resolved; see class-level Javadoc.
 * @param reason        why this value was chosen; never null
 * @param flagKey       the key of the flag that was evaluated; never null
 * @param variant       the variant label when {@code reason == VARIANT}, otherwise {@code null}.
 *                      For string-typed flags this is the variant's raw string value
 *                      (e.g. {@code "control"}, {@code "treatment-a"}); for boolean/number
 *                      flags it is the stringified primitive; for object flags it is {@code null}.
 * @param matchedRuleId the {@code name} of the rule that produced this result when a rule
 *                      matched (reasons {@code TARGETING_MATCH}, {@code SPLIT}, {@code VARIANT}),
 *                      otherwise {@code null}.
 * @since 1.0.0 ({@code value}, {@code reason}, {@code flagKey});
 *        {@code variant} and {@code matchedRuleId} added in 1.1.0
 */
public record EvaluationResult<T>(
        T value,
        EvaluationReason reason,
        String flagKey,
        String variant,
        String matchedRuleId
) {

    public EvaluationResult {
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(flagKey, "flagKey must not be null");
    }

    /**
     * Convenience factory for results that carry no variant or rule information.
     * Use this instead of the canonical 5-arg constructor for error and short-circuit
     * paths ({@code FLAG_NOT_FOUND}, {@code FLAG_DISABLED}, {@code PROVIDER_ERROR}, etc.).
     * Both {@code variant} and {@code matchedRuleId} will be {@code null} in the returned result.
     *
     * @param <T>      the value type
     * @param value    the resolved (or default) value
     * @param reason   the evaluation reason; never null
     * @param flagKey  the flag key; never null
     * @return a result with {@code variant = null} and {@code matchedRuleId = null}
     * @since 1.1.0
     */
    public static <T> EvaluationResult<T> of(T value, EvaluationReason reason, String flagKey) {
        return new EvaluationResult<>(value, reason, flagKey, null, null);
    }
}
