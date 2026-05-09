package io.github.eliasss3990.openflags.provider.file;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;

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
    private Duration watchDebounce = FileWatcher.DEFAULT_DEBOUNCE;

    FileFlagProviderBuilder() {}

    /**
     * Sets the path to the flag configuration file (required).
     *
     * @param path the file path
     * @return this builder
     * @throws NullPointerException if path is null
     */
    public FileFlagProviderBuilder path(Path path) {
        this.path = Objects.requireNonNull(path, "path must not be null");
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
        Objects.requireNonNull(path, "path must not be null");
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
     * <p>
     * <b>Symlinks (Docker / Kubernetes ConfigMap)</b>: Java's {@link java.nio.file.WatchService}
     * registers the inode of the resolved target, not the symlink itself. ConfigMap volume
     * mounts in Kubernetes (and similar atomic-swap mechanisms) replace the underlying
     * directory rather than rewriting the file in place, so the watcher's registration
     * becomes orphaned and changes are not detected. If you mount the flag file via a
     * symlink that may be re-pointed at runtime, prefer polling at the application level
     * or restart the process when the ConfigMap changes.
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
     * Sets the debounce window applied by the underlying file watcher.
     * Default: {@link FileWatcher#DEFAULT_DEBOUNCE}. Has no effect when
     * watching is disabled.
     *
     * @param watchDebounce strictly positive debounce window
     * @return this builder
     * @throws NullPointerException     if {@code watchDebounce} is null
     * @throws IllegalArgumentException if {@code watchDebounce} is zero or negative
     */
    public FileFlagProviderBuilder watchDebounce(Duration watchDebounce) {
        Objects.requireNonNull(watchDebounce, "watchDebounce must not be null");
        if (watchDebounce.isZero() || watchDebounce.isNegative()) {
            throw new IllegalArgumentException("watchDebounce must be > 0, got " + watchDebounce);
        }
        this.watchDebounce = watchDebounce;
        return this;
    }

    /**
     * Builds the {@link FileFlagProvider}.
     * <p>
     * Does <strong>not</strong> call {@code init()} — initialization happens when
     * {@link FileFlagProvider#init()} is called or when the client is built via
     * {@link io.github.eliasss3990.openflags.core.OpenFlagsClientBuilder}.
     * </p>
     *
     * @return a configured (but not yet initialized) provider
     * @throws IllegalStateException if no path was set
     */
    public FileFlagProvider build() {
        if (path == null) {
            throw new IllegalStateException("A file path must be set before building the provider");
        }
        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException(
                    "path must point to a file, not a directory: " + path);
        }
        return new FileFlagProvider(path, watchEnabled, watchDebounce);
    }
}
