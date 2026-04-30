package com.openflags.spring;

import com.openflags.core.OpenFlagsClient;
import com.openflags.core.provider.FlagProvider;
import com.openflags.provider.file.FileFlagProvider;
import com.openflags.provider.hybrid.HybridFlagProvider;
import com.openflags.provider.remote.RemoteFlagProvider;
import com.openflags.provider.remote.RemoteProviderConfig;
import com.openflags.provider.remote.RemoteFlagProviderBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * Spring Boot auto-configuration for openflags.
 * <p>
 * Creates an {@link OpenFlagsClient} bean backed by the provider configured via
 * {@link OpenFlagsProperties}. Conditional on {@link OpenFlagsClient} being on the classpath.
 * </p>
 *
 * <h2>Classpath resources and file watching</h2>
 * <p>
 * When the configured path uses the {@code classpath:} prefix, the auto-configuration
 * resolves it to a filesystem {@link Path}. If the resource lives inside a JAR
 * (i.e., cannot be resolved to a real filesystem path), file watching is automatically
 * disabled with an INFO log message. This is a known limitation of
 * {@link java.nio.file.WatchService}.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(OpenFlagsProperties.class)
@ConditionalOnClass(OpenFlagsClient.class)
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
    public RemoteFlagProvider remoteFlagProvider(OpenFlagsProperties properties) {
        OpenFlagsProperties.RemoteProperties r = properties.getRemote();
        if (r.getBaseUrl() == null) {
            throw new IllegalStateException(
                    "openflags.remote.base-url is required when openflags.provider=remote");
        }
        RemoteFlagProviderBuilder builder = RemoteFlagProviderBuilder.forUrl(r.getBaseUrl())
                .flagsPath(r.getFlagsPath())
                .requestTimeout(r.getRequestTimeout())
                .pollInterval(r.getPollInterval())
                .cacheTtl(r.getCacheTtl())
                .userAgent(r.getUserAgent());
        if (r.getAuthHeaderName() != null && !r.getAuthHeaderName().isBlank()) {
            builder.apiKey(r.getAuthHeaderName(), r.getAuthHeaderValue());
        }
        return builder.build();
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
    public HybridFlagProvider hybridFlagProvider(OpenFlagsProperties props) {
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
                r.getAuthHeaderValue(),
                r.getConnectTimeout(),
                r.getRequestTimeout(),
                r.getPollInterval(),
                r.getCacheTtl(),
                r.getUserAgent());

        return HybridFlagProvider.builder()
                .remoteConfig(rc)
                .snapshotPath(h.getSnapshotPath())
                .snapshotFormat(h.getSnapshotFormat())
                .watchSnapshot(h.isWatchSnapshot())
                .snapshotDebounce(h.getSnapshotDebounce())
                .failIfNoFallback(h.isFailIfNoFallback())
                .build();
    }

    /**
     * Creates a {@link FlagProvider} bean backed by a local file.
     * Activated when {@code openflags.provider=file} (the default).
     *
     * @param properties   the openflags properties
     * @param resourceLoader Spring resource loader for resolving classpath paths
     * @return a configured file provider; init/shutdown are managed by the Spring container
     * @throws IOException if the resource cannot be resolved
     */
    @Bean(initMethod = "init", destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "openflags.provider", havingValue = "file", matchIfMissing = true)
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
     * Back-off: skipped if a custom {@code OpenFlagsClient} bean is already defined.
     *
     * @param provider the configured flag provider
     * @return a ready-to-use client
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenFlagsClient openFlagsClient(FlagProvider provider) {
        return OpenFlagsClient.builder().provider(provider).build();
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

    private record ResolvedFile(Path path, boolean watchEnabled) {}

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    static class ActuatorConfiguration {
        @Bean
        @ConditionalOnMissingBean
        public OpenFlagsHealthIndicator openFlagsHealthIndicator(OpenFlagsClient client) {
            return new OpenFlagsHealthIndicator(client);
        }
    }
}
