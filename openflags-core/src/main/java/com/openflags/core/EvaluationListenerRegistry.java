package com.openflags.core;

import com.openflags.core.evaluation.EvaluationEvent;
import com.openflags.core.evaluation.EvaluationListener;
import com.openflags.core.metrics.MetricsRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds the {@link EvaluationListener}s registered on an
 * {@link OpenFlagsClient} and dispatches {@link EvaluationEvent}s to
 * them. Package-private: it is an implementation detail of
 * {@code com.openflags.core}.
 *
 * <p>
 * Listeners are stored in a {@link CopyOnWriteArrayList} so that
 * registration changes never race against an in-flight dispatch. Each
 * listener is invoked inside its own {@code try/catch}: a faulty
 * listener never breaks the evaluation result and never blocks the
 * next listener in the chain. Failures are forwarded to the
 * {@link MetricsRecorder} and rate-limited at {@code WARN} (powers of
 * two) per listener class.
 */
final class EvaluationListenerRegistry {

    private static final Logger log = LoggerFactory.getLogger(EvaluationListenerRegistry.class);

    private final List<EvaluationListener> listeners = new CopyOnWriteArrayList<>();
    /**
     * Per-listener-instance failure counters. Keyed by the listener reference
     * itself so that two distinct registrations of equivalent lambdas track
     * independent counts; entries are removed in
     * {@link #remove(EvaluationListener)}
     * to prevent unbounded growth across the registry's lifetime.
     */
    private final ConcurrentHashMap<EvaluationListener, AtomicLong> failureCounters = new ConcurrentHashMap<>();
    private final MetricsRecorder metrics;

    EvaluationListenerRegistry(MetricsRecorder metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    void add(EvaluationListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    boolean remove(EvaluationListener listener) {
        if (listener == null) {
            return false;
        }
        boolean removed = listeners.remove(listener);
        if (removed) {
            // Residual race: a concurrent dispatch() may re-insert the counter
            // via computeIfAbsent after this remove. Acceptable: the entry is
            // bounded (single AtomicLong) and would only persist until the next
            // remove() of the same instance. Locking the whole registry just to
            // close this window is not worth it.
            failureCounters.remove(listener);
        }
        return removed;
    }

    int size() {
        return listeners.size();
    }

    void dispatch(EvaluationEvent event) {
        if (listeners.isEmpty()) {
            return;
        }
        for (EvaluationListener listener : listeners) {
            try {
                listener.onEvaluation(event);
            } catch (RuntimeException ex) {
                handleFailure(listener, ex);
            }
        }
    }

    private void handleFailure(EvaluationListener listener, RuntimeException ex) {
        String name = listener.getClass().getSimpleName();
        if (name.isEmpty()) {
            name = listener.getClass().getName();
        }
        try {
            metrics.recordListenerError(name);
        } catch (RuntimeException metricsFailure) {
            log.debug("openflags: metrics.recordListenerError threw", metricsFailure);
        }
        long count = failureCounters
                .computeIfAbsent(listener, k -> new AtomicLong())
                .incrementAndGet();
        if (Long.bitCount(count) == 1) {
            log.warn("openflags: EvaluationListener {} threw {} times (rate-limited)",
                    name, count, ex);
        }
    }
}
