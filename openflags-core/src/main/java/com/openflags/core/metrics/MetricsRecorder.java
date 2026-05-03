package com.openflags.core.metrics;

import com.openflags.core.evaluation.EvaluationEvent;
import com.openflags.core.event.ChangeType;

import java.util.function.Supplier;

/**
 * SDK-agnostic metrics sink. Implementations adapt to a concrete metrics
 * library (e.g. Micrometer) without leaking that library's types into
 * the public API of {@code openflags-core}.
 *
 * <p>
 * The default implementation is {@link #NOOP}, used when no metrics
 * backend is configured. The Spring Boot starter wires a
 * {@code MicrometerMetricsRecorder} when Micrometer is on the classpath.
 *
 * @since 0.5.0
 */
public interface MetricsRecorder {

    /**
     * Records a single flag evaluation.
     *
     * @param event the evaluation event, never {@code null}
     */
    void recordEvaluation(EvaluationEvent event);

    /**
     * Records the outcome of a remote provider poll cycle.
     *
     * @param outcome       short tag (e.g. {@code "success"}, {@code "failure"},
     *                      {@code "not_modified"}); callers must not pass {@code null}
     * @param durationNanos wall-clock duration of the poll; callers must
     *                      pass a non-negative value
     */
    void recordPoll(String outcome, long durationNanos);

    /**
     * Records the outcome of a snapshot write performed by a hybrid or
     * cache-backed provider.
     *
     * @param outcome       short tag (e.g. {@code "success"}, {@code "failure"});
     *                      callers must not pass {@code null}
     * @param durationNanos wall-clock duration of the write; callers must
     *                      pass a non-negative value
     */
    void recordSnapshotWrite(String outcome, long durationNanos);

    /**
     * Records that a flag changed (created, updated or deleted).
     *
     * @param type kind of change, never {@code null}
     */
    void recordFlagChange(ChangeType type);

    /**
     * Records that a hybrid provider switched its routing target between
     * its remote and file backends.
     *
     * @param from previous routing target (e.g. {@code "remote"})
     * @param to   new routing target (e.g. {@code "file"})
     */
    void recordHybridFallback(String from, String to);

    /**
     * Records that an {@code EvaluationListener} threw an exception during
     * dispatch.
     *
     * @param listenerSimpleName simple class name of the failing listener
     */
    void recordListenerError(String listenerSimpleName);

    /**
     * Records that a {@link com.openflags.core.provider.FlagProvider} threw an
     * unexpected (non-{@code ProviderException}) exception while resolving a flag.
     * Implementations may use this to alert on programming errors that bypass
     * the documented provider contract.
     *
     * @param flagKey key of the flag being evaluated; never {@code null}
     * @since 1.2.0
     */
    default void recordUnexpectedProviderError(String flagKey) {}

    /**
     * Registers a gauge whose value is read on demand from the supplier.
     * Each call replaces any previously registered gauge with the same
     * name and tags.
     *
     * @param name     metric name, never {@code null}
     * @param tags     tags to attach, never {@code null}
     * @param supplier supplies the current value, never {@code null}
     */
    void registerGauge(String name, Iterable<Tag> tags, Supplier<Number> supplier);

    /** No-op singleton used when metrics are disabled or unavailable. */
    MetricsRecorder NOOP = NoopMetricsRecorder.INSTANCE;
}
