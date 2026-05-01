package com.openflags.core.evaluation;

/**
 * Receives an {@link EvaluationEvent} after every flag evaluation.
 *
 * <p>
 * Listeners are invoked synchronously on the evaluating thread. They
 * MUST be non-blocking and MUST NOT throw checked exceptions. Any
 * runtime exception thrown by a listener is caught by the dispatcher in
 * {@link com.openflags.core.OpenFlagsClient} and surfaced through the
 * metrics channel; it never breaks the evaluation result.
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
