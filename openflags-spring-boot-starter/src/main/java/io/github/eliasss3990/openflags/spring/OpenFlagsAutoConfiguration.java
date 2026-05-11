package io.github.eliasss3990.openflags.spring;

import io.github.eliasss3990.openflags.core.OpenFlagsClient;
import io.github.eliasss3990.openflags.core.OpenFlagsClientBuilder;
import io.github.eliasss3990.openflags.core.OpenFlagsClientCustomizer;
import io.github.eliasss3990.openflags.core.evaluation.EvaluationListener;
import io.github.eliasss3990.openflags.core.metrics.MetricsRecorder;
import io.github.eliasss3990.openflags.core.provider.FlagProvider;
import io.github.eliasss3990.openflags.core.provider.ProviderDiagnostics;
import io.github.eliasss3990.openflags.provider.file.FileFlagProvider;
import io.github.eliasss3990.openflags.provider.hybrid.HybridFlagProvider;
import io.github.eliasss3990.openflags.provider.remote.MetricsRecordingPollListener;
import io.github.eliasss3990.openflags.provider.remote.RemoteFlagProvider;
import io.github.eliasss3990.openflags.provider.remote.RemoteProviderConfig;
import io.github.eliasss3990.openflags.provider.remote.RemoteFlagProviderBuilder;
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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
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
 * auto-configuration resolves it to a filesystem {@link Path}:
 * </p>
 * <ul>
 * <li>If the resource is on the filesystem (typical {@code mvn spring-boot:run},
 * {@code target/classes/...}) it is used directly. Watching is honored.</li>
 * <li>If the resource lives inside a JAR (typical Spring Boot launcher JAR in
 * production) and {@code openflags.file.watch=false}, the resource is
 * extracted to a temp file with a deterministic name (hash of the original
 * path) and used as the provider's path. The same resource always maps to
 * the same temp file across restarts; the content is refreshed on each
 * startup. The bundled flags become a read-only baseline.</li>
 * <li>If the resource lives inside a JAR and {@code openflags.file.watch=true},
 * startup fails fast with a descriptive error — {@link java.nio.file.WatchService}
 * cannot watch entries inside an archive. Either disable {@code watch} or
 * point to a {@code file:} path that supports it.</li>
 * </ul>
 *
 * <h2>Observability</h2>
 * <p>
 * When Micrometer is on the classpath the starter wires the provider with a
 * {@link io.github.eliasss3990.openflags.core.metrics.MetricsRecorder} backed by the
 * application's
 * {@code MeterRegistry}. The {@link OpenFlagsHealthIndicator} bean is published
 * automatically when Spring Boot Actuator is present; it surfaces
 * {@code provider.type}, {@code provider.state} and any provider-specific
 * diagnostics exposed via
 * {@link io.github.eliasss3990.openflags.core.provider.ProviderDiagnostics}.
 * </p>
 */
@AutoConfiguration
@AutoConfigureAfter(name = "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration")
@EnableConfigurationProperties(OpenFlagsProperties.class)
@ConditionalOnClass(OpenFlagsClient.class)
@Import(MicrometerBindings.class)
public class OpenFlagsAutoConfiguration {

    /**
     * Constructor injection of the bound properties so validation runs as part
     * of auto-configuration instantiation, before any provider {@code @Bean}
     * method is evaluated. Failing here surfaces an actionable error message
     * instead of cascading into a confusing "no FlagProvider bean" error.
     *
     * @param properties the bound configuration properties
     */
    public OpenFlagsAutoConfiguration(OpenFlagsProperties properties) {
        properties.validate();
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

        var builder = FileFlagProvider.builder()
                .path(resolved.path())
                .watchEnabled(resolved.watchEnabled());
        if (resolved.watchEnabled()) {
            // Only thread the debounce when the watcher will actually run, so
            // misconfigurations that don't matter (watch disabled) can't trip
            // the builder's strict validation.
            builder.watchDebounce(properties.getFile().getDebounce());
        }
        return builder.build();
    }

    /**
     * Creates the {@link OpenFlagsClient} bean.
     * Back-off: skipped if a custom {@code OpenFlagsClient} bean is already
     * defined. All {@link OpenFlagsClientCustomizer} beans are applied in
     * {@code @Order} order before {@link OpenFlagsClientBuilder#build()}.
     *
     * @param provider    the configured flag provider
     * @param properties  the bound openflags properties (used for audit/MDC
     *                    settings)
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
        boolean insideArchive = "jar".equals(scheme) || "zip".equals(scheme);
        if (insideArchive) {
            if (watchRequested) {
                throw new IOException(
                        "Flag file '" + pathStr + "' is inside a JAR and cannot be watched via WatchService. "
                                + "Either disable openflags.file.watch or extract the resource to a "
                                + "filesystem path (file:/path/to/flags.yml).");
            }
            // Read-only mode: extract the resource to a temp file so FileFlagProvider
            // gets a real filesystem Path. The temp file uses a deterministic name
            // (hash of originalPath) so restarts reuse the same path — sin acumular
            // copias en /tmp. Permisos 0600 cuando el FS lo soporta. Cleanup queda
            // delegado al OS (tmpwatch / tmpfs reboot).
            return new ResolvedFile(extractToTempFile(resource, pathStr), false);
        }
        return new ResolvedFile(Path.of(uri), watchRequested);
    }

    /**
     * Extrae el recurso a un temp file con nombre <b>determinístico</b>
     * (hash del path original). Mismo recurso → mismo path entre restarts:
     * el contenido se reemplaza vía {@code REPLACE_EXISTING} y {@code /tmp}
     * no acumula copias en crash-loops o deploys frecuentes, incluso si la
     * JVM termina sin shutdown hook (SIGKILL).
     *
     * <p>Lifecycle: el archivo persiste hasta que el OS lo limpie
     * (tmpfs reboot, {@code tmpwatch}, etc.). Si el path original cambia
     * (ej. rename de {@code flags.yml} → {@code feature-flags.yml}), el
     * archivo viejo queda huérfano hasta que el OS lo recoja.
     *
     * <p>Permisos: en filesystems POSIX se crea con {@code 0600}
     * (owner-only). El {@code java.io.tmpdir} default ({@code /tmp}) suele
     * ser world-readable; limitar permisos a nivel de archivo evita que
     * otros procesos locales lean flags sensibles. En filesystems no-POSIX
     * (Windows con NTFS, contenedores con overlay sin POSIX) cae al default
     * del OS.
     *
     * <p>Pre: {@code watchRequested=false}. Si en algún momento se cambia el
     * contrato, ajustar el caller.
     */
    private static Path extractToTempFile(Resource resource, String originalPath) throws IOException {
        String suffix = suffixForPath(originalPath);
        String hash = Integer.toHexString(originalPath.hashCode() & 0x7fffffff);
        Path temp = Path.of(System.getProperty("java.io.tmpdir"),
                "openflags-flags-" + hash + suffix);
        ensureOwnerOnlyFile(temp);
        try (var in = resource.getInputStream()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        return temp;
    }

    /**
     * Crea el file con permisos 0600 si el filesystem soporta POSIX y el
     * file todavía no existe. Si ya existe (caso típico en restart con
     * mismo path determinístico) los permisos del file anterior persisten,
     * lo que es deseado — el copy con {@code REPLACE_EXISTING} sobreescribe
     * el contenido sin tocar los permisos. En filesystems no-POSIX no hace
     * nada y deja al OS aplicar su default.
     */
    private static void ensureOwnerOnlyFile(Path temp) throws IOException {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return;
        }
        try {
            Files.createFile(temp,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } catch (FileAlreadyExistsException ignored) {
            // Permisos del file existente se preservan; REPLACE_EXISTING del copy
            // sobreescribe solo el contenido, lo cual es el comportamiento deseado
            // entre restarts (mismo file, mismo dueño, contenido refrescado).
        }
    }

    private static String suffixForPath(String pathStr) {
        int dot = pathStr.lastIndexOf('.');
        if (dot < 0 || dot == pathStr.length() - 1) {
            return ".yml";
        }
        String ext = pathStr.substring(dot).toLowerCase(Locale.ROOT);
        return ".yml".equals(ext) || ".yaml".equals(ext) || ".json".equals(ext) ? ext : ".yml";
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

    /**
     * Auto-configuration for the {@link RemoteFlagProvider}.
     * <p>
     * Class-level {@link ConditionalOnClass} on {@link RemoteFlagProvider} keeps
     * the entire
     * configuration off the context (and skips bean post-processing of its method
     * signatures)
     * when the {@code openflags-provider-remote} module is absent — the dependency
     * is declared
     * {@code <optional>true</optional>} in the starter POM.
     * </p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RemoteFlagProvider.class)
    @ConditionalOnProperty(prefix = "openflags", name = "provider", havingValue = "remote")
    static class RemoteProviderConfiguration {

        @Bean(initMethod = "init", destroyMethod = "shutdown")
        @ConditionalOnMissingBean(FlagProvider.class)
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
    }

    /**
     * Auto-configuration for the {@link HybridFlagProvider}.
     * <p>
     * Class-level {@link ConditionalOnClass} on {@link HybridFlagProvider} keeps
     * the entire configuration off the context when the
     * {@code openflags-provider-hybrid} module is absent — the dependency is
     * declared {@code <optional>true</optional>} in the starter POM.
     * </p>
     * <p>
     * The helper {@code remoteProviderConfigFromProperties} lives inside this
     * inner class on purpose: keeping any reference to {@code RemoteProviderConfig}
     * out of the outer class signatures prevents Spring's {@code getDeclaredMethods()}
     * introspection of {@link OpenFlagsAutoConfiguration} from triggering a class
     * load of {@code RemoteProviderConfig} when {@code openflags-provider-remote}
     * is not on the classpath. The same applies to any future helper that needs
     * Remote/Hybrid types.
     * </p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HybridFlagProvider.class)
    @ConditionalOnProperty(prefix = "openflags", name = "provider", havingValue = "hybrid")
    static class HybridProviderConfiguration {

        @Bean(initMethod = "init", destroyMethod = "shutdown")
        @ConditionalOnMissingBean(FlagProvider.class)
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

            RemoteProviderConfig rc = remoteProviderConfigFromProperties(r);

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

        private static RemoteProviderConfig remoteProviderConfigFromProperties(OpenFlagsProperties.RemoteProperties r) {
            return new RemoteProviderConfig(
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
