package com.openflags.core.evaluation;

/**
 * Explains why a particular value was returned during flag evaluation.
 */
public enum EvaluationReason {
    /** Flag was found and its value resolved normally. */
    RESOLVED,
    /** Flag key was not found in the provider; the caller's default value was used. */
    FLAG_NOT_FOUND,
    /** Flag exists but is disabled; the caller's default value was used. */
    FLAG_DISABLED,
    /** Flag type does not match the requested type; the caller's default value was used. */
    TYPE_MISMATCH,
    /** Provider encountered an error during resolution; the caller's default value was used. */
    PROVIDER_ERROR
}
