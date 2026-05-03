package com.openflags.core;

import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationEvent;
import com.openflags.core.evaluation.EvaluationListener;
import com.openflags.core.evaluation.EvaluationResult;
import com.openflags.core.evaluation.FlagEvaluator;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.metrics.MetricsRecorder;
import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main entry point for the openflags SDK.
 * <p>
 * Provides typed methods to evaluate feature flags backed by a
 * {@link FlagProvider}.
 * Thread-safe and intended to be used as a singleton. Obtain an instance via
 * {@link #builder()}.
 * </p>
 *
 * <h2>Lifecycle</h2>
 * 
 * <pre>
 * OpenFlagsClient client = OpenFlagsClient.builder()
 *         .provider(myProvider)
 *         .build();
 *
 * boolean enabled = client.getBooleanValue("my-feature", false);
 *
 * client.shutdown(); // release resources when done
 * </pre>
 *
 * <p>
 * Calling any evaluation method after {@link #shutdown()} throws
 * {@link IllegalStateException}.
 * </p>
 */
public final class OpenFlagsClient {

    private static final Logger log = LoggerFactory.getLogger(OpenFlagsClient.class);

    private static final String MDC_FLAG_KEY = OpenFlagsMdc.FLAG_KEY;
    private static final String MDC_TARGETING_KEY = OpenFlagsMdc.TARGETING_KEY;

    private final FlagProvider provider;
    private final FlagEvaluator evaluator;
    private final MetricsRecorder metrics;
    private final EvaluationListenerRegistry listeners;
    private final String providerType;
    private final boolean auditMdcEnabled;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    OpenFlagsClient(FlagProvider provider, FlagEvaluator evaluator) {
        this(provider, evaluator, MetricsRecorder.NOOP,
                new EvaluationListenerRegistry(MetricsRecorder.NOOP),
                "unknown", false);
    }

    OpenFlagsClient(FlagProvider provider,
            FlagEvaluator evaluator,
            MetricsRecorder metrics,
            EvaluationListenerRegistry listeners,
            String providerType,
            boolean auditMdcEnabled) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.listeners = Objects.requireNonNull(listeners, "listeners must not be null");
        this.providerType = Objects.requireNonNull(providerType, "providerType must not be null");
        this.auditMdcEnabled = auditMdcEnabled;
    }

    /**
     * Creates a new builder for {@code OpenFlagsClient}.
     *
     * @return a new builder
     */
    public static OpenFlagsClientBuilder builder() {
        return new OpenFlagsClientBuilder();
    }

    // --- Boolean ---

    /**
     * Evaluates a boolean flag.
     *
     * @param key          the flag key
     * @param defaultValue value returned if the flag is missing, disabled, or wrong
     *                     type
     * @return the flag value, or {@code defaultValue}
     * @throws IllegalStateException if the client has been shut down
     */
    public boolean getBooleanValue(String key, boolean defaultValue) {
        return getBooleanValue(key, defaultValue, EvaluationContext.empty());
    }

    /**
     * Evaluates a boolean flag with evaluation context.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @param context      evaluation context for targeting and split rules
     * @return the flag value, or {@code defaultValue}
     * @throws IllegalStateException if the client has been shut down
     */
    public boolean getBooleanValue(String key, boolean defaultValue, EvaluationContext context) {
        return getBooleanResult(key, defaultValue, context).value();
    }

    /**
     * Evaluates a boolean flag and returns the full result including the resolution
     * reason.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @param context      evaluation context
     * @return the evaluation result; never null
     * @throws IllegalStateException if the client has been shut down
     */
    public EvaluationResult<Boolean> getBooleanResult(String key, boolean defaultValue, EvaluationContext context) {
        return evaluateAndDispatch(key, Boolean.class, defaultValue, context,
                () -> evaluator.evaluate(provider, key, Boolean.class, defaultValue, context));
    }

    /**
     * Convenience overload for {@link #getBooleanResult(String, boolean, EvaluationContext)}
     * with an empty evaluation context.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @return the evaluation result; never null
     * @throws IllegalStateException if the client has been shut down
     * @since 1.1
     */
    public EvaluationResult<Boolean> getBooleanResult(String key, boolean defaultValue) {
        return getBooleanResult(key, defaultValue, EvaluationContext.empty());
    }

    // --- String ---

    /**
     * Evaluates a string flag.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @return the flag value, or {@code defaultValue}
     * @throws IllegalStateException if the client has been shut down
     */
    public String getStringValue(String key, String defaultValue) {
        return getStringValue(key, defaultValue, EvaluationContext.empty());
    }

    /**
     * Evaluates a string flag with evaluation context.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @param context      evaluation context for targeting and split rules
     * @return the flag value, or {@code defaultValue}
     * @throws IllegalStateException if the client has been shut down
     */
    public String getStringValue(String key, String defaultValue, EvaluationContext context) {
        return getStringResult(key, defaultValue, context).value();
    }

    /**
     * Evaluates a string flag and returns the full result including the resolution
     * reason.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @param context      evaluation context
     * @return the evaluation result; never null
     * @throws IllegalStateException if the client has been shut down
     */
    public EvaluationResult<String> getStringResult(String key, String defaultValue, EvaluationContext context) {
        return evaluateAndDispatch(key, String.class, defaultValue, context,
                () -> evaluator.evaluate(provider, key, String.class, defaultValue, context));
    }

    /**
     * Convenience overload for {@link #getStringResult(String, String, EvaluationContext)}
     * with an empty evaluation context.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @return the evaluation result; never null
     * @throws IllegalStateException if the client has been shut down
     * @since 1.1
     */
    public EvaluationResult<String> getStringResult(String key, String defaultValue) {
        return getStringResult(key, defaultValue, EvaluationContext.empty());
    }

    // --- Number ---

    /**
     * Evaluates a number flag.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @return the flag value, or {@code defaultValue}
     * @throws IllegalStateException if the client has been shut down
     */
    public double getNumberValue(String key, double defaultValue) {
        return getNumberValue(key, defaultValue, EvaluationContext.empty());
    }

    /**
     * Evaluates a number flag with evaluation context.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @param context      evaluation context for targeting and split rules
     * @return the flag value, or {@code defaultValue}
     * @throws IllegalStateException if the client has been shut down
     */
    public double getNumberValue(String key, double defaultValue, EvaluationContext context) {
        return getNumberResult(key, defaultValue, context).value();
    }

    /**
     * Evaluates a number flag and returns the full result including the resolution
     * reason.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @param context      evaluation context
     * @return the evaluation result; never null
     * @throws IllegalStateException if the client has been shut down
     */
    public EvaluationResult<Double> getNumberResult(String key, double defaultValue, EvaluationContext context) {
        return evaluateAndDispatch(key, Double.class, defaultValue, context,
                () -> evaluator.evaluate(provider, key, Double.class, defaultValue, context));
    }

    /**
     * Convenience overload for {@link #getNumberResult(String, double, EvaluationContext)}
     * with an empty evaluation context.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @return the evaluation result; never null
     * @throws IllegalStateException if the client has been shut down
     * @since 1.1
     */
    public EvaluationResult<Double> getNumberResult(String key, double defaultValue) {
        return getNumberResult(key, defaultValue, EvaluationContext.empty());
    }

    // --- Object ---

    /**
     * Evaluates an object (JSON) flag.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @return the flag value, or {@code defaultValue}
     * @throws IllegalStateException if the client has been shut down
     */
    public Map<String, Object> getObjectValue(String key, Map<String, Object> defaultValue) {
        return getObjectValue(key, defaultValue, EvaluationContext.empty());
    }

    /**
     * Evaluates an object (JSON) flag with evaluation context.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @param context      evaluation context for targeting and split rules
     * @return the flag value, or {@code defaultValue}
     * @throws IllegalStateException if the client has been shut down
     */
    public Map<String, Object> getObjectValue(String key, Map<String, Object> defaultValue, EvaluationContext context) {
        return getObjectResult(key, defaultValue, context).value();
    }

    /**
     * Evaluates an object (JSON) flag and returns the full result including the
     * resolution reason.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @param context      evaluation context
     * @return the evaluation result; never null
     * @throws IllegalStateException if the client has been shut down
     */
    @SuppressWarnings("unchecked")
    public EvaluationResult<Map<String, Object>> getObjectResult(
            String key, Map<String, Object> defaultValue, EvaluationContext context) {
        return evaluateAndDispatch(key, Map.class, defaultValue, context,
                () -> (EvaluationResult<Map<String, Object>>) (EvaluationResult<?>) evaluator.evaluate(provider, key,
                        Map.class, defaultValue, context));
    }

    /**
     * Convenience overload for {@link #getObjectResult(String, Map, EvaluationContext)}
     * with an empty evaluation context.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @return the evaluation result; never null
     * @throws IllegalStateException if the client has been shut down
     * @since 1.1
     */
    public EvaluationResult<Map<String, Object>> getObjectResult(String key, Map<String, Object> defaultValue) {
        return getObjectResult(key, defaultValue, EvaluationContext.empty());
    }

    // --- Listeners ---

    /**
     * Registers a listener for flag change events emitted by the underlying
     * provider.
     *
     * @param listener the listener to add; must not be null
     * @throws IllegalStateException if the client has been shut down
     */
    public void addChangeListener(FlagChangeListener listener) {
        requireNotShutdown();
        provider.addChangeListener(listener);
    }

    /**
     * Removes a previously registered flag change listener.
     * <p>
     * Safe to call after {@link #shutdown()}: behaves as a no-op so
     * cleanup paths can run unconditionally.
     * </p>
     *
     * @param listener the listener to remove
     */
    public void removeChangeListener(FlagChangeListener listener) {
        if (shutdown.get())
            return;
        provider.removeChangeListener(listener);
    }

    /**
     * Registers an {@link EvaluationListener} that receives an event for
     * every subsequent flag evaluation.
     *
     * @param listener the listener; must not be null
     * @throws IllegalStateException if the client has been shut down
     * @throws NullPointerException  if listener is null
     */
    public void addEvaluationListener(EvaluationListener listener) {
        requireNotShutdown();
        listeners.add(listener);
    }

    /**
     * Removes a previously registered {@link EvaluationListener}. Safe
     * to call after {@link #shutdown()} (returns {@code false}).
     *
     * @param listener the listener to remove
     * @return {@code true} if the listener was registered
     */
    public boolean removeEvaluationListener(EvaluationListener listener) {
        if (shutdown.get())
            return false;
        return listeners.remove(listener);
    }

    // --- Lifecycle ---

    /**
     * Returns the current lifecycle state of the underlying provider.
     *
     * @return the provider state; never null
     */
    public ProviderState getProviderState() {
        return provider.getState();
    }

    /**
     * Shuts down the client and its underlying provider, releasing all
     * resources. Idempotent: calling on an already shut-down client is
     * a no-op. After shutdown, evaluation methods throw
     * {@link IllegalStateException}.
     */
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            provider.shutdown();
        }
    }

    private void requireNotShutdown() {
        if (shutdown.get()) {
            throw new IllegalStateException("OpenFlagsClient has been shut down");
        }
    }

    @FunctionalInterface
    private interface EvaluationStep<T> {
        EvaluationResult<T> evaluate();
    }

    private <T> EvaluationResult<T> evaluateAndDispatch(String key,
            Class<?> type,
            Object defaultValue,
            EvaluationContext context,
            EvaluationStep<T> step) {
        requireNotShutdown();
        Map<String, String> mdcPrev = pushMdc(key, context);
        long start = System.nanoTime();
        try {
            EvaluationResult<T> result = step.evaluate();
            long duration = System.nanoTime() - start;
            EvaluationEvent event = new EvaluationEvent(
                    key,
                    type,
                    defaultValue,
                    result.value(),
                    result.reason(),
                    null,
                    null,
                    context,
                    Instant.now(),
                    duration,
                    providerType);
            try {
                metrics.recordEvaluation(event);
            } catch (RuntimeException e) {
                // metrics failures must never break the evaluation result
                log.debug("MetricsRecorder.recordEvaluation threw {}: {}",
                        e.getClass().getSimpleName(), e.getMessage());
            }
            listeners.dispatch(event);
            return result;
        } finally {
            popMdc(mdcPrev);
        }
    }

    private Map<String, String> pushMdc(String key, EvaluationContext context) {
        if (!auditMdcEnabled) {
            return null;
        }
        Map<String, String> previous = new HashMap<>(2);
        previous.put(MDC_FLAG_KEY, MDC.get(MDC_FLAG_KEY));
        previous.put(MDC_TARGETING_KEY, MDC.get(MDC_TARGETING_KEY));
        MDC.put(MDC_FLAG_KEY, key);
        String targetingKey = context.getTargetingKey().orElse(null);
        if (targetingKey != null) {
            MDC.put(MDC_TARGETING_KEY, targetingKey);
        } else {
            MDC.remove(MDC_TARGETING_KEY);
        }
        return previous;
    }

    private void popMdc(Map<String, String> previous) {
        if (previous == null) {
            return;
        }
        restoreMdc(MDC_FLAG_KEY, previous.get(MDC_FLAG_KEY));
        restoreMdc(MDC_TARGETING_KEY, previous.get(MDC_TARGETING_KEY));
    }

    private static void restoreMdc(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
