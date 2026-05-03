package com.openflags.core.evaluation;

/**
 * The result of evaluating a feature flag.
 * <p>
 * Contains the resolved value, the reason why that value was chosen, and the flag key
 * that was evaluated. Implemented as a Java record (ADR-009).
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
 * @param <T>     the type of the resolved value
 * @param value   the resolved value, or the caller's default. May be {@code null}
 *                only when the caller's default is {@code null} and the flag could
 *                not be resolved; see class-level Javadoc.
 * @param reason  why this value was chosen; never null
 * @param flagKey the key of the flag that was evaluated; never null
 */
public record EvaluationResult<T>(
        T value,
        EvaluationReason reason,
        String flagKey
) {}
