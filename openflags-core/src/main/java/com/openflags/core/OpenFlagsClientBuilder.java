package com.openflags.core;

import com.openflags.core.evaluation.EvaluationListener;
import com.openflags.core.evaluation.FlagEvaluator;
import com.openflags.core.evaluation.rule.RuleEngine;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.metrics.MetricsRecorder;
import com.openflags.core.provider.FlagProvider;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builder for {@link OpenFlagsClient}.
 * <p>
 * Requires a {@link FlagProvider}; all other settings are optional.
 * </p>
 *
 * <pre>
 * OpenFlagsClient client = OpenFlagsClient.builder()
 *         .provider(myProvider)
 *         .build();
 * </pre>
 */
public final class OpenFlagsClientBuilder {

    private static final String MICROMETER_REGISTRY_CLASS = "io.micrometer.core.instrument.MeterRegistry";
    private static final String MICROMETER_RECORDER_CLASS = "com.openflags.core.metrics.MicrometerMetricsRecorder";

    private FlagProvider provider;
    private MetricsRecorder metricsRecorder;
    private Object micrometerRegistry;
    private boolean metricsTagFlagKey = true;
    private boolean auditMdcEnabled = false;
    private String providerType = "unknown";
    private final List<EvaluationListener> evaluationListeners = new ArrayList<>();

    OpenFlagsClientBuilder() {
    }

    /**
     * Sets the {@link MetricsRecorder} directly. Use this when the caller
     * already has a recorder bean (typical in Spring contexts) instead of the
     * reflective {@link #metricsRegistry(Object)} path. When both are set,
     * the explicitly provided recorder wins.
     *
     * @param recorder the recorder to use; must not be null
     * @return this builder
     * @throws NullPointerException if recorder is null
     */
    public OpenFlagsClientBuilder metricsRecorder(MetricsRecorder recorder) {
        this.metricsRecorder = Objects.requireNonNull(recorder, "recorder must not be null");
        return this;
    }

    /**
     * Sets the flag provider (required).
     *
     * @param provider the provider to use; must not be null
     * @return this builder
     * @throws NullPointerException if provider is null
     */
    public OpenFlagsClientBuilder provider(FlagProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        return this;
    }

    /**
     * Registers an evaluation listener. Listeners are invoked in
     * registration order on the evaluating thread, after metrics have
     * been recorded. Exceptions are caught and reported through the
     * configured {@link MetricsRecorder}.
     *
     * @param listener the listener; must not be null
     * @return this builder
     * @throws NullPointerException if listener is null
     */
    public OpenFlagsClientBuilder addEvaluationListener(EvaluationListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        evaluationListeners.add(listener);
        return this;
    }

    /**
     * Removes a previously registered listener (if it was added through
     * this builder). Useful for {@code OpenFlagsClientCustomizer}s that
     * want to revoke a default listener.
     *
     * @param listener the listener to remove; null tolerated as no-op
     * @return this builder
     */
    public OpenFlagsClientBuilder removeEvaluationListener(EvaluationListener listener) {
        if (listener != null) {
            evaluationListeners.remove(listener);
        }
        return this;
    }

    /**
     * Provides a Micrometer {@code MeterRegistry} via reflection so that
     * {@code openflags-core} keeps Micrometer as an optional dependency
     * (ADR-501). The argument MUST be assignable to
     * {@code io.micrometer.core.instrument.MeterRegistry}; otherwise
     * {@link IllegalArgumentException} is thrown.
     *
     * @param registry a {@code MeterRegistry} instance; must not be null
     * @return this builder
     * @throws NullPointerException     if registry is null
     * @throws IllegalStateException    if Micrometer is not on the classpath
     * @throws IllegalArgumentException if registry is not a {@code MeterRegistry}
     */
    public OpenFlagsClientBuilder metricsRegistry(Object registry) {
        Objects.requireNonNull(registry, "registry must not be null");
        Class<?> meterRegistryClass;
        try {
            meterRegistryClass = Class.forName(MICROMETER_REGISTRY_CLASS,
                    false, registry.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Micrometer is not on the classpath; add io.micrometer:micrometer-core "
                            + "to use metricsRegistry(...)",
                    e);
        }
        if (!meterRegistryClass.isInstance(registry)) {
            throw new IllegalArgumentException(
                    "registry must implement " + MICROMETER_REGISTRY_CLASS
                            + " (got " + registry.getClass().getName() + ")");
        }
        this.micrometerRegistry = registry;
        return this;
    }

    /**
     * Controls whether the {@code flag} and {@code variant} tags are
     * attached to evaluation metrics. Disable for high-cardinality
     * deployments. Default {@code true}.
     *
     * @param enabled true to tag with flag key, false to omit
     * @return this builder
     */
    public OpenFlagsClientBuilder metricsTagFlagKey(boolean enabled) {
        this.metricsTagFlagKey = enabled;
        return this;
    }

    /**
     * Enables MDC keys {@code openflags.flag_key} and
     * {@code openflags.targeting_key} during the scope of every
     * evaluation. Disabled by default since the targeting key is often
     * personally identifiable information.
     *
     * @param enabled true to publish MDC keys
     * @return this builder
     */
    public OpenFlagsClientBuilder auditMdcEnabled(boolean enabled) {
        this.auditMdcEnabled = enabled;
        return this;
    }

    /**
     * Sets the provider type label used in metrics (e.g. {@code "file"},
     * {@code "remote"}, {@code "hybrid"}). Defaults to
     * {@code "unknown"}.
     *
     * @param providerType label; must not be null
     * @return this builder
     */
    public OpenFlagsClientBuilder providerType(String providerType) {
        this.providerType = Objects.requireNonNull(providerType, "providerType must not be null");
        return this;
    }

    /**
     * Builds the {@link OpenFlagsClient}.
     * <p>
     * Calls {@link FlagProvider#init()} on the provider before returning. Since
     * {@code init()}
     * is idempotent, calling it on an already-initialized provider is safe.
     * </p>
     *
     * @return a ready-to-use client
     * @throws IllegalStateException if no provider was set
     * @throws ProviderException     if provider initialization fails
     */
    public OpenFlagsClient build() {
        if (provider == null) {
            throw new IllegalStateException("A FlagProvider must be set before building the client");
        }
        provider.init();
        MetricsRecorder recorder = resolveMetricsRecorder();
        EvaluationListenerRegistry registry = new EvaluationListenerRegistry(recorder);
        for (EvaluationListener listener : evaluationListeners) {
            registry.add(listener);
        }
        return new OpenFlagsClient(
                provider,
                new FlagEvaluator(new RuleEngine(), recorder),
                recorder,
                registry,
                providerType,
                auditMdcEnabled);
    }

    private MetricsRecorder resolveMetricsRecorder() {
        if (metricsRecorder != null) {
            return metricsRecorder;
        }
        if (micrometerRegistry == null) {
            return MetricsRecorder.NOOP;
        }
        try {
            ClassLoader cl = micrometerRegistry.getClass().getClassLoader();
            Class<?> meterRegistryClass = Class.forName(MICROMETER_REGISTRY_CLASS, false, cl);
            Class<?> recorderClass = Class.forName(MICROMETER_RECORDER_CLASS, false,
                    OpenFlagsClientBuilder.class.getClassLoader());
            Constructor<?> ctor = recorderClass.getDeclaredConstructor(
                    meterRegistryClass, boolean.class);
            ctor.setAccessible(true);
            return (MetricsRecorder) ctor.newInstance(micrometerRegistry, metricsTagFlagKey);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Micrometer is not on the classpath; remove metricsRegistry(...) "
                            + "or add io.micrometer:micrometer-core",
                    e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to instantiate MicrometerMetricsRecorder reflectively", e);
        }
    }
}
