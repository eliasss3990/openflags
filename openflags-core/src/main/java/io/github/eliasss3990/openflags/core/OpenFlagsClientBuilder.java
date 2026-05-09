package io.github.eliasss3990.openflags.core;

import io.github.eliasss3990.openflags.core.evaluation.EvaluationListener;
import io.github.eliasss3990.openflags.core.evaluation.FlagEvaluator;
import io.github.eliasss3990.openflags.core.evaluation.rule.RuleEngine;
import io.github.eliasss3990.openflags.core.exception.ProviderException;
import io.github.eliasss3990.openflags.core.metrics.MetricsRecorder;
import io.github.eliasss3990.openflags.core.metrics.MicrometerMetricsRecorder;
import io.github.eliasss3990.openflags.core.provider.FlagProvider;

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

    private FlagProvider provider;
    private MetricsRecorder metricsRecorder;
    private Object pendingMicrometerRegistry;
    private boolean metricsTagFlagKey = true;
    private boolean auditMdcEnabled = false;
    private String providerType = "unknown";
    private final List<EvaluationListener> evaluationListeners = new ArrayList<>();

    OpenFlagsClientBuilder() {
    }

    /**
     * Sets the {@link MetricsRecorder} directly. This is the canonical typed
     * entry point for metrics integration; prefer it over the deprecated
     * reflective {@link #metricsRegistry(Object)}. When both are set, the
     * recorder set most recently wins.
     *
     * <p>For Micrometer users on a manual builder path:
     * <pre>{@code
     * OpenFlagsClient client = OpenFlagsClient.builder()
     *         .provider(provider)
     *         .metricsRecorder(new MicrometerMetricsRecorder(meterRegistry, true))
     *         .build();
     * }</pre>
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
     * Provides a Micrometer {@code MeterRegistry}. The argument must implement
     * {@code io.micrometer.core.instrument.MeterRegistry}.
     *
     * @param registry a {@code MeterRegistry} instance; must not be null
     * @return this builder
     * @throws NullPointerException     if registry is null
     * @throws IllegalStateException    if Micrometer is not on the classpath
     * @throws IllegalArgumentException if registry is not a {@code MeterRegistry}
     * @deprecated Use {@link #metricsRecorder(MetricsRecorder)} with a
     *             {@code io.github.eliasss3990.openflags.core.metrics.MicrometerMetricsRecorder}
     *             instance. The reflective bridge will be removed in 2.0 (ADR-4).
     *             <p>Migration:
     *             <pre>{@code
     * // Before
     * OpenFlagsClient client = OpenFlagsClient.builder()
     *         .provider(provider)
     *         .metricsRegistry(meterRegistry)
     *         .build();
     *
     * // After
     * OpenFlagsClient client = OpenFlagsClient.builder()
     *         .provider(provider)
     *         .metricsRecorder(new MicrometerMetricsRecorder(meterRegistry, true))
     *         .build();
     *             }</pre>
     */
    @Deprecated(forRemoval = true, since = "1.1.0")
    public OpenFlagsClientBuilder metricsRegistry(Object registry) {
        Objects.requireNonNull(registry, "registry must not be null");
        // Eagerly validate the type so callers get the same fast-fail
        // semantics as before: Micrometer-missing surfaces here, not at
        // build(). Resolution to a recorder is still deferred so that a
        // later metricsTagFlagKey(...) call is honored.
        try {
            MicrometerMetricsRecorder.validateRegistryObject(registry);
        } catch (LinkageError e) {
            throw new IllegalStateException(
                    "Micrometer is not on the classpath; add io.micrometer:micrometer-core "
                            + "or use metricsRecorder(MetricsRecorder) directly",
                    e);
        }
        this.pendingMicrometerRegistry = registry;
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
        MetricsRecorder recorder;
        if (metricsRecorder != null) {
            recorder = metricsRecorder;
        } else if (pendingMicrometerRegistry != null) {
            recorder = MicrometerMetricsRecorder.fromRegistryObject(
                    pendingMicrometerRegistry, metricsTagFlagKey);
        } else {
            recorder = MetricsRecorder.NOOP;
        }
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

}
