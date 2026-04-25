package com.openflags.core;

import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationResult;
import com.openflags.core.evaluation.FlagEvaluator;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderState;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main entry point for the openflags SDK.
 * <p>
 * Provides typed methods to evaluate feature flags backed by a {@link FlagProvider}.
 * Thread-safe and intended to be used as a singleton. Obtain an instance via
 * {@link #builder()}.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <pre>
 * OpenFlagsClient client = OpenFlagsClient.builder()
 *     .provider(myProvider)
 *     .build();
 *
 * boolean enabled = client.getBooleanValue("my-feature", false);
 *
 * client.shutdown(); // release resources when done
 * </pre>
 *
 * <p>Calling any evaluation method after {@link #shutdown()} throws {@link IllegalStateException}.</p>
 */
public final class OpenFlagsClient {

    private final FlagProvider provider;
    private final FlagEvaluator evaluator;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    OpenFlagsClient(FlagProvider provider, FlagEvaluator evaluator) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
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
     * @param defaultValue value returned if the flag is missing, disabled, or wrong type
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
     * @param defaultValue value returned if the flag is missing, disabled, or wrong type
     * @param context      evaluation context
     * @return the flag value, or {@code defaultValue}
     * @throws IllegalStateException if the client has been shut down
     */
    public boolean getBooleanValue(String key, boolean defaultValue, EvaluationContext context) {
        return getBooleanResult(key, defaultValue, context).value();
    }

    /**
     * Evaluates a boolean flag and returns the full result including the resolution reason.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @param context      evaluation context
     * @return the evaluation result; never null
     * @throws IllegalStateException if the client has been shut down
     */
    public EvaluationResult<Boolean> getBooleanResult(String key, boolean defaultValue, EvaluationContext context) {
        requireNotShutdown();
        return evaluator.evaluate(provider, key, Boolean.class, defaultValue, context);
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
     * @param context      evaluation context
     * @return the flag value, or {@code defaultValue}
     * @throws IllegalStateException if the client has been shut down
     */
    public String getStringValue(String key, String defaultValue, EvaluationContext context) {
        return getStringResult(key, defaultValue, context).value();
    }

    /**
     * Evaluates a string flag and returns the full result including the resolution reason.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @param context      evaluation context
     * @return the evaluation result; never null
     * @throws IllegalStateException if the client has been shut down
     */
    public EvaluationResult<String> getStringResult(String key, String defaultValue, EvaluationContext context) {
        requireNotShutdown();
        return evaluator.evaluate(provider, key, String.class, defaultValue, context);
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
     * @param context      evaluation context
     * @return the flag value, or {@code defaultValue}
     * @throws IllegalStateException if the client has been shut down
     */
    public double getNumberValue(String key, double defaultValue, EvaluationContext context) {
        return getNumberResult(key, defaultValue, context).value();
    }

    /**
     * Evaluates a number flag and returns the full result including the resolution reason.
     *
     * @param key          the flag key
     * @param defaultValue fallback value
     * @param context      evaluation context
     * @return the evaluation result; never null
     * @throws IllegalStateException if the client has been shut down
     */
    public EvaluationResult<Double> getNumberResult(String key, double defaultValue, EvaluationContext context) {
        requireNotShutdown();
        return evaluator.evaluate(provider, key, Double.class, defaultValue, context);
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
     * @param context      evaluation context
     * @return the flag value, or {@code defaultValue}
     * @throws IllegalStateException if the client has been shut down
     */
    public Map<String, Object> getObjectValue(String key, Map<String, Object> defaultValue, EvaluationContext context) {
        return getObjectResult(key, defaultValue, context).value();
    }

    /**
     * Evaluates an object (JSON) flag and returns the full result including the resolution reason.
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
        requireNotShutdown();
        return (EvaluationResult<Map<String, Object>>) (EvaluationResult<?>) evaluator.evaluate(
                provider, key, Map.class, defaultValue, context);
    }

    // --- Listeners ---

    /**
     * Registers a listener for flag change events emitted by the underlying provider.
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
     * Safe to call after {@link #shutdown()}: behaves as a no-op in that case
     * to allow cleanup paths to run unconditionally.
     * </p>
     *
     * @param listener the listener to remove
     */
    public void removeChangeListener(FlagChangeListener listener) {
        if (shutdown.get()) return;
        provider.removeChangeListener(listener);
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
     * Shuts down the client and its underlying provider, releasing all resources.
     * <p>
     * This method is idempotent: calling it on an already shut-down client is a no-op.
     * After shutdown, any evaluation method throws {@link IllegalStateException}.
     * </p>
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
}
