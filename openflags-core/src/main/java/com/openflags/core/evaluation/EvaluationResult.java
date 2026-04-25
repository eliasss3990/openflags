package com.openflags.core.evaluation;

/**
 * The result of evaluating a feature flag.
 * <p>
 * Contains the resolved value, the reason why that value was chosen, and the flag key
 * that was evaluated. Implemented as a Java record (ADR-009).
 * </p>
 *
 * @param <T>     the type of the resolved value
 * @param value   the resolved value (may be the caller's default if the flag was not found
 *                or an error occurred)
 * @param reason  why this value was chosen
 * @param flagKey the key of the flag that was evaluated
 */
public record EvaluationResult<T>(
        T value,
        EvaluationReason reason,
        String flagKey
) {}
