package com.openflags.spring;

import com.openflags.core.OpenFlagsClient;
import com.openflags.core.provider.FlagProvider;
import com.openflags.provider.file.FileFlagProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
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
 * <h3>Classpath resources and file watching</h3>
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

    private static final Logger log = LoggerFactory.getLogger(OpenFlagsAutoConfiguration.class);

    /**
     * Creates a {@link FlagProvider} bean backed by a local file.
     * Activated when {@code openflags.provider=file} (the default).
     *
     * @param properties   the openflags properties
     * @param resourceLoader Spring resource loader for resolving classpath paths
     * @return a configured (not yet initialized) file provider
     * @throws IOException if the resource cannot be resolved
     */
    @Bean
    @ConditionalOnProperty(name = "openflags.provider", havingValue = "file", matchIfMissing = true)
    public FlagProvider fileFlagProvider(OpenFlagsProperties properties, ResourceLoader resourceLoader)
            throws IOException {
        String pathStr = properties.getFile().getPath();
        boolean watchEnabled = properties.getFile().isWatchEnabled();

        Path resolvedPath = resolveFilePath(pathStr, resourceLoader, watchEnabled);
        boolean effectiveWatchEnabled = determineEffectiveWatchEnabled(pathStr, resourceLoader, watchEnabled);

        return FileFlagProvider.builder()
                .path(resolvedPath)
                .watchEnabled(effectiveWatchEnabled)
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

    private Path resolveFilePath(String pathStr, ResourceLoader resourceLoader, boolean watchEnabled)
            throws IOException {
        Resource resource = resourceLoader.getResource(pathStr);
        if (!resource.exists()) {
            throw new IOException("openflags flag file not found: " + pathStr);
        }
        try {
            URI uri = resource.getURI();
            if (uri.getScheme().equals("jar") || uri.getScheme().equals("zip")) {
                // Cannot resolve to filesystem — return a temp copy or throw
                throw new IOException(
                        "Flag file '" + pathStr + "' is inside a JAR and cannot be resolved to a filesystem path. "
                                + "Use a file: path or extract the resource to a configurable location.");
            }
            return Path.of(uri);
        } catch (IOException e) {
            throw new IOException("Cannot resolve flag file path: " + pathStr, e);
        }
    }

    private boolean determineEffectiveWatchEnabled(String pathStr, ResourceLoader resourceLoader,
            boolean requestedWatch) {
        if (!requestedWatch) return false;
        Resource resource = resourceLoader.getResource(pathStr);
        try {
            URI uri = resource.getURI();
            if (uri.getScheme().equals("jar") || uri.getScheme().equals("zip")) {
                log.info("openflags: file watching disabled for '{}' because the file is inside a JAR. "
                        + "WatchService cannot observe files inside JARs.", pathStr);
                return false;
            }
        } catch (IOException e) {
            log.info("openflags: file watching disabled for '{}': cannot determine resource location.", pathStr);
            return false;
        }
        return true;
    }
}
