package com.openflags.core.evaluation.rule;

/**
 * Comparison operators supported by {@link Condition}.
 * <p>
 * See the project documentation for the precise type expectations of each operator.
 * Operators are evaluated in a type-tolerant fashion at runtime: a type-incompatible
 * comparison evaluates to {@code false} rather than throwing.
 * </p>
 */
public enum Operator {
    /** Equality (with numeric coercion via double). */
    EQ,
    /** Inequality. */
    NEQ,
    /** Set membership; {@code expectedValue} must be a List. */
    IN,
    /** Set non-membership. */
    NOT_IN,
    /** Strictly greater than (numeric). */
    GT,
    /** Greater than or equal (numeric). */
    GTE,
    /** Strictly less than (numeric). */
    LT,
    /** Less than or equal (numeric). */
    LTE,
    /** Substring containment (String). */
    CONTAINS,
    /** Prefix match (String). */
    STARTS_WITH,
    /** Suffix match (String). */
    ENDS_WITH,
    /** Regex match; {@code expectedValue} must be a precompiled {@link java.util.regex.Pattern}. */
    MATCHES
}
