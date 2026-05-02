package com.openflags.spring;

import com.openflags.core.OpenFlagsClient;
import com.openflags.core.OpenFlagsClientBuilder;
import com.openflags.core.OpenFlagsClientCustomizer;
import com.openflags.core.evaluation.EvaluationListener;
import com.openflags.core.metrics.MetricsRecorder;
import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderDiagnostics;
import com.openflags.provider.file.FileFlagProvider;
import com.openflags.provider.hybrid.HybridFlagProvider;
import com.openflags.provider.remote.MetricsRecordingPollListener;
import com.openflags.provider.remote.RemoteFlagProvider;
import com.openflags.provider.remote.RemoteProviderConfig;
import com.openflags.provider.remote.RemoteFlagProviderBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Spring Boot auto-configuration for openflags.
 * <p>
 * Creates an {@link OpenFlagsClient} bean backed by the provider selected via
 * {@code openflags.provider}. Conditional on {@link OpenFlagsClient} being on
 * the classpath.
 * </p>
 *
 * <h2>Provider selection</h2>
 * <p>
 * The provider is chosen by {@code openflags.provider} (default {@code file}):
 * </p>
 * <ul>
 * <li>{@code file} — local YAML/JSON file. Requires
 * {@code openflags.file.path};
 * supports {@code classpath:} and {@code file:} prefixes. See "Classpath
 * resources and file watching" below.</li>
 * <li>{@code remote} — HTTP polling against a flag service. Requires
 * {@code openflags.remote.base-url}. Polling, cache TTL, request timeout and
 * the circuit breaker (failure threshold, max backoff) are all configurable
 * under {@code openflags.remote.*}.</li>
 * <li>{@code hybrid} — remote with a local file fallback. Reuses
 * {@code openflags.remote.*} for the remote half and {@code openflags.hybrid.*}
 * for the snapshot file (path, format, watch, debounce,
 * fail-if-no-fallback).</li>
 * </ul>
 *
 * <h2>Classpath resources and file watching</h2>
 * <p>
 * When the configured path uses the {@code classpath:} prefix the
 * auto-configuration resolves it to a filesystem {@link Path}. If the resource
 * lives inside a JAR (i.e., cannot be resolved to a real filesystem path) the
 * provider bean fails to initialize with a descriptive error (Spring wraps it
 * in a {@code BeanCreationException} at startup) so that the deployment is not
 * silently degraded. Use a {@code file:} path or extract the flag file to a
 * configurable filesystem location for production deployments. This restriction
 * stems from {@link java.nio.file.WatchService} not supporting paths inside
 * JARs.
 * </p>
 *
 * <h2>Observability</h2>
 * <p>
 * When Micrometer is on the classpath the starter wires the provider with a
 * {@link com.openflags.core.metrics.MetricsRecorder} backed by the
 * application's
 * {@code MeterRegistry}. The {@link OpenFlagsHealthIndicator} bean is published
 * automatically when Spring Boot Actuator is present; it surfaces
 * {@code provider.type}, {@code provider.state} and any provider-specific
 * diagnostics exposed via
 * {@link com.openflags.core.provider.ProviderDiagnostics}.
 * </p>
 */
@AutoConfiguration
@AutoConfigureAfter(name = "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration")
@EnableConfigurationProperties(OpenFlagsProperties.class)
@ConditionalOnClass(OpenFlagsClient.class)
@Import(MicrometerBindings.class)
public class OpenFlagsAutoConfiguration {

    /**
     * Creates a {@link RemoteFlagProvider} bean.
     * Activated when {@code openflags.provider=remote}.
     *
     * @param properties the openflags properties
     * @return a configured and initialized remote provider
     */
    @Bean(initMethod = "init", destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "openflags", name = "provider", havingValue = "remote")
    @ConditionalOnMissingBean(FlagProvider.class)
    @ConditionalOnClass(name = "com.openflags.provider.remote.RemoteFlagProvider")
    public RemoteFlagProvider remoteFlagProvider(OpenFlagsProperties properties,
            ObjectProvider<MetricsRecorder> metricsRecorderProvider) {
        OpenFlagsProperties.RemoteProperties r = properties.getRemote();
        if (r.getBaseUrl() == null) {
            throw new IllegalStateException(
                    "openflags.remote.base-url is required when openflags.provider=remote");
        }
        RemoteFlagProviderBuilder builder = RemoteFlagProviderBuilder.forUrl(r.getBaseUrl())
                .flagsPath(r.getFlagsPath())
                .connectTimeout(r.getConnectTimeout())
                .requestTimeout(r.getRequestTimeout())
                .pollInterval(r.getPollInterval())
                .cacheTtl(r.getCacheTtl())
                .userAgent(r.getUserAgent())
                .failureThreshold(r.getFailureThreshold())
                .maxBackoff(r.getMaxBackoff());
        if (r.getAuthHeaderName() != null && !r.getAuthHeaderName().isBlank()) {
            builder.apiKey(r.getAuthHeaderName(), r.getAuthHeaderSecret());
        }
        RemoteFlagProvider provider = builder.build();
        MetricsRecorder recorder = metricsRecorderProvider.getIfAvailable();
        if (recorder != null) {
            provider.setPollListener(new MetricsRecordingPollListener(recorder));
        }
        return provider;
    }

    /**
     * Creates a {@link HybridFlagProvider} bean.
     * Activated when {@code openflags.provider=hybrid}.
     * Reuses {@code openflags.remote.*} for the remote config and
     * {@code openflags.hybrid.*} for the snapshot settings.
     *
     * @param props the openflags properties
     * @return a configured and initialized hybrid provider
     */
    @Bean(initMethod = "init", destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "openflags", name = "provider", havingValue = "hybrid")
    @ConditionalOnMissingBean(FlagProvider.class)
    @ConditionalOnClass(name = "com.openflags.provider.hybrid.HybridFlagProvider")
    public HybridFlagProvider hybridFlagProvider(OpenFlagsProperties props,
            ObjectProvider<MetricsRecorder> metricsRecorderProvider) {
        OpenFlagsProperties.RemoteProperties r = props.getRemote();
        OpenFlagsProperties.HybridProperties h = props.getHybrid();

        if (r.getBaseUrl() == null) {
            throw new IllegalStateException(
                    "openflags.remote.base-url must be set when openflags.provider=hybrid");
        }
        if (h.getSnapshotPath() == null || h.getSnapshotPath().isBlank()) {
            throw new IllegalStateException(
                    "openflags.hybrid.snapshot-path must be set when openflags.provider=hybrid");
        }

        RemoteProviderConfig rc = new RemoteProviderConfig(
                r.getBaseUrl(),
                r.getFlagsPath(),
                r.getAuthHeaderName(),
                r.getAuthHeaderSecret(),
                r.getConnectTimeout(),
                r.getRequestTimeout(),
                r.getPollInterval(),
                r.getCacheTtl(),
                r.getUserAgent(),
                r.getFailureThreshold(),
                r.getMaxBackoff());

        HybridFlagProvider provider = HybridFlagProvider.builder()
                .remoteConfig(rc)
                .snapshotPath(h.getSnapshotPath())
                .snapshotFormat(h.getSnapshotFormat())
                .watchSnapshot(h.isWatchSnapshot())
                .snapshotDebounce(h.getSnapshotDebounce())
                .failIfNoFallback(h.isFailIfNoFallback())
                .build();
        MetricsRecorder recorder = metricsRecorderProvider.getIfAvailable();
        if (recorder != null) {
            provider.setMetricsRecorder(recorder);
        }
        return provider;
    }

    /**
     * Creates a {@link FlagProvider} bean backed by a local file.
     * Activated when {@code openflags.provider=file} (the default).
     * Skipped if a custom {@link FlagProvider} bean is already defined.
     *
     * @param properties     the openflags properties
     * @param resourceLoader Spring resource loader for resolving classpath paths
     * @return a configured file provider; init/shutdown are managed by the Spring
     *         container
     * @throws IOException if the resource cannot be resolved
     */
    @Bean(initMethod = "init", destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "openflags.provider", havingValue = "file", matchIfMissing = true)
    @ConditionalOnMissingBean(FlagProvider.class)
    public FlagProvider fileFlagProvider(OpenFlagsProperties properties, ResourceLoader resourceLoader)
            throws IOException {
        String pathStr = properties.getFile().getPath();
        boolean watchRequested = properties.getFile().isWatchEnabled();

        ResolvedFile resolved = resolveFile(pathStr, resourceLoader, watchRequested);

        return FileFlagProvider.builder()
                .path(resolved.path())
                .watchEnabled(resolved.watchEnabled())
                .build();
    }

    /**
     * Creates the {@link OpenFlagsClient} bean.
     * Back-off: skipped if a custom {@code OpenFlagsClient} bean is already
     * defined. All {@link OpenFlagsClientCustomizer} beans are applied in
     * {@code @Order} order before {@link OpenFlagsClientBuilder#build()}.
     *
     * @param provider    the configured flag provider
     * @param customizers all client customizer beans, applied in order before build
     * @return a ready-to-use client
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenFlagsClient openFlagsClient(FlagProvider provider,
            OpenFlagsProperties properties,
            ObjectProvider<OpenFlagsClientCustomizer> customizers) {
        OpenFlagsClientBuilder builder = OpenFlagsClient.builder()
                .provider(provider)
                .providerType(resolveProviderType(provider, properties))
                .auditMdcEnabled(properties.getAudit().isMdcEnabled());
        customizers.orderedStream().forEach(c -> c.customize(builder));
        return builder.build();
    }

    private static String resolveProviderType(FlagProvider provider, OpenFlagsProperties properties) {
        if (provider instanceof ProviderDiagnostics d) {
            String type = d.providerType();
            if (type != null && !type.isBlank()) {
                return type.toLowerCase(Locale.ROOT);
            }
        }
        String configured = properties.getProvider();
        return configured == null ? "unknown" : configured.toLowerCase(Locale.ROOT);
    }

    private ResolvedFile resolveFile(String pathStr, ResourceLoader resourceLoader, boolean watchRequested)
            throws IOException {
        Resource resource = resourceLoader.getResource(pathStr);
        if (!resource.exists()) {
            throw new IOException("openflags flag file not found: " + pathStr);
        }
        URI uri;
        try {
            uri = resource.getURI();
        } catch (IOException e) {
            throw new IOException("Cannot resolve flag file path: " + pathStr, e);
        }
        String scheme = uri.getScheme();
        if ("jar".equals(scheme) || "zip".equals(scheme)) {
            throw new IOException(
                    "Flag file '" + pathStr + "' is inside a JAR and cannot be resolved to a filesystem path. "
                            + "Use a file: path or extract the resource to a configurable location.");
        }
        return new ResolvedFile(Path.of(uri), watchRequested);
    }

    private record ResolvedFile(Path path, boolean watchEnabled) {
    }

    /**
     * Auto-detection of {@link EvaluationListener} beans in the application
     * context.
     * <p>
     * Every bean of type {@link EvaluationListener} is registered on the
     * {@link OpenFlagsClient} builder in {@code @Order} order via an
     * {@link OpenFlagsClientCustomizer}. This lets applications attach evaluation
     * listeners (audit, MDC, custom telemetry) declaratively without owning the
     * client construction.
     * </p>
     */
    @Configuration(proxyBeanMethods = false)
    static class EvaluationListenersConfiguration {

        /**
         * Customizer that registers every {@link EvaluationListener} bean on the
         * client builder in order.
         *
         * @param listeners the discovered evaluation listener beans
         * @return a customizer that wires the listeners onto the builder
         */
        @Bean
        @ConditionalOnMissingBean(name = "evaluationListenersCustomizer")
        OpenFlagsClientCustomizer evaluationListenersCustomizer(
                ObjectProvider<EvaluationListener> listeners) {
            return builder -> listeners.orderedStream().forEach(builder::addEvaluationListener);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    static class ActuatorConfiguration {
        @Bean
        @ConditionalOnMissingBean
        public OpenFlagsHealthIndicator openFlagsHealthIndicator(OpenFlagsClient client,
                ObjectProvider<FlagProvider> providers) {
            return new OpenFlagsHealthIndicator(client, providers.getIfAvailable());
        }
    }
}
