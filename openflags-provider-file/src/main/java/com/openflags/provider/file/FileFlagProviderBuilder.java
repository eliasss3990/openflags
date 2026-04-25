package com.openflags.provider.file;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Builder for {@link FileFlagProvider}.
 * <p>
 * Requires a file {@link #path(Path) path}; all other settings are optional.
 * </p>
 *
 * <pre>
 * FileFlagProvider provider = FileFlagProvider.builder()
 *     .path(Path.of("flags.yml"))
 *     .watchEnabled(true)
 *     .build();
 * </pre>
 */
public final class FileFlagProviderBuilder {

    private Path path;
    private boolean watchEnabled = true;

    FileFlagProviderBuilder() {}

    /**
     * Sets the path to the flag configuration file (required).
     *
     * @param path the file path
     * @return this builder
     * @throws NullPointerException if path is null
     */
    public FileFlagProviderBuilder path(Path path) {
        if (path == null) throw new NullPointerException("path must not be null");
        this.path = path;
        return this;
    }

    /**
     * Sets the path to the flag configuration file from a string (required).
     *
     * @param path the file path as a string
     * @return this builder
     * @throws NullPointerException if path is null
     */
    public FileFlagProviderBuilder path(String path) {
        if (path == null) throw new NullPointerException("path must not be null");
        this.path = Paths.get(path);
        return this;
    }

    /**
     * Enables or disables hot reload via file watching. Default: {@code true}.
     * <p>
     * Note: even if enabled, file watching is automatically disabled when the file path
     * points to a classpath resource inside a JAR. WatchService cannot observe files
     * inside JARs. An INFO message is logged in that case.
     * </p>
     *
     * @param watchEnabled {@code true} to enable hot reload, {@code false} to disable
     * @return this builder
     */
    public FileFlagProviderBuilder watchEnabled(boolean watchEnabled) {
        this.watchEnabled = watchEnabled;
        return this;
    }

    /**
     * Builds the {@link FileFlagProvider}.
     * <p>
     * Does <strong>not</strong> call {@code init()} — initialization happens when
     * {@link FileFlagProvider#init()} is called or when the client is built via
     * {@link com.openflags.core.OpenFlagsClientBuilder}.
     * </p>
     *
     * @return a configured (but not yet initialized) provider
     * @throws IllegalStateException if no path was set
     */
    public FileFlagProvider build() {
        if (path == null) {
            throw new IllegalStateException("A file path must be set before building the provider");
        }
        return new FileFlagProvider(path, watchEnabled);
    }
}
