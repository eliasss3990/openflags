package com.openflags.core.evaluation;

/**
 * Receives an {@link EvaluationEvent} after every flag evaluation.
 *
 * <p>
 * Listeners are invoked synchronously on the evaluating thread. They
 * MUST be non-blocking and MUST NOT throw checked exceptions. Runtime
 * exceptions are caught by the dispatcher and reported via the metrics
 * channel; they never break the evaluation result.
 *
 * <p>
 * Implementations may safely re-enter the
 * {@link com.openflags.core.OpenFlagsClient} to
 * evaluate additional flags. MDC keys set by the surrounding evaluation
 * are restored after the listener returns.
 *
 * @since 0.5.0
 */
@FunctionalInterface
public interface EvaluationListener {

    /**
     * Called once per evaluation, after the value has been resolved.
     *
     * @param event the evaluation event, never {@code null}
     */
    void onEvaluation(EvaluationEvent event);
}
