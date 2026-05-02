/**
 * Evaluation observability primitives. Defines
 * {@link com.openflags.core.evaluation.EvaluationEvent} (immutable record
 * dispatched after every flag evaluation) and the
 * {@link com.openflags.core.evaluation.EvaluationListener} functional
 * interface used to plug audit, tracing and custom telemetry sinks.
 *
 * <p>
 * Listener invocations happen synchronously on the evaluation thread.
 * Implementations must not block; the {@code OpenFlagsClient} traps
 * exceptions per listener so a faulty listener cannot break evaluation.
 *
 * @since 0.5.0
 */
package com.openflags.core.evaluation;
