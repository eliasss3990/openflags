package com.openflags.core.evaluation;

/**
 * Explains why a particular value was returned during flag evaluation.
 */
public enum EvaluationReason {
    /** Flag was found and no rules were declared; the flag's default value was used. */
    RESOLVED,
    /** Flag has rules; a {@link com.openflags.core.evaluation.rule.TargetingRule} matched the context. */
    TARGETING_MATCH,
    /** Flag has rules; a {@link com.openflags.core.evaluation.rule.SplitRule} matched the bucket. */
    SPLIT,
    /** Flag has rules; a {@link com.openflags.core.evaluation.rule.MultiVariantRule} matched and a variant was selected. */
    VARIANT,
    /** Flag has rules; none matched, the flag's default value was used. */
    DEFAULT,
    /** Flag key was not found in the provider; the caller's default value was used. */
    FLAG_NOT_FOUND,
    /** Flag exists but is disabled; the caller's default value was used. */
    FLAG_DISABLED,
    /** Flag type does not match the requested type; the caller's default value was used. */
    TYPE_MISMATCH,
    /** Provider encountered an error during resolution; the caller's default value was used. */
    PROVIDER_ERROR
}
