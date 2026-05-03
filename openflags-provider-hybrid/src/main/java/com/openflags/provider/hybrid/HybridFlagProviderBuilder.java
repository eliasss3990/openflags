package com.openflags.provider.hybrid;

import com.openflags.provider.remote.RemoteProviderConfig;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;

/**
 * Fluent builder for {@link HybridFlagProvider}. Mirrors the style of
 * {@code FileFlagProviderBuilder} and {@code RemoteFlagProviderBuilder}.
 *
 * <pre>
 * HybridFlagProvider provider = HybridFlagProvider.builder()
 *     .remoteConfig(remoteConfig)
 *     .snapshotPath(Path.of("/var/lib/openflags/snapshot.json"))
 *     .snapshotFormat(SnapshotFormat.JSON)
 *     .build();
 * </pre>
 */
public final class HybridFlagProviderBuilder {

    private RemoteProviderConfig remoteConfig;
    private Path snapshotPath;
    private SnapshotFormat snapshotFormat = SnapshotFormat.JSON;
    private boolean watchSnapshot = true;
    private Duration snapshotDebounce = HybridProviderConfig.DEFAULT_SNAPSHOT_DEBOUNCE;
    private boolean failIfNoFallback = false;

    HybridFlagProviderBuilder() {}

    /**
     * Sets the remote provider configuration.
     *
     * @param remoteConfig the remote config; non-null
     * @return this builder
     */
    public HybridFlagProviderBuilder remoteConfig(RemoteProviderConfig remoteConfig) {
        this.remoteConfig = Objects.requireNonNull(remoteConfig, "remoteConfig must not be null");
        return this;
    }

    /**
     * Convenience: builds a remote config with all defaults from the given base URL.
     * Equivalent to {@code remoteConfig(RemoteProviderConfig.defaults(baseUrl))}.
     *
     * @param baseUrl the remote base URL; non-null, http or https
     * @return this builder
     * @since 1.1
     */
    public HybridFlagProviderBuilder remoteUrl(URI baseUrl) {
        return remoteConfig(RemoteProviderConfig.defaults(baseUrl));
    }

    /**
     * Convenience overload for {@link #remoteUrl(URI)} taking a string.
     *
     * @param baseUrl the remote base URL; non-null
     * @return this builder
     * @since 1.1
     */
    public HybridFlagProviderBuilder remoteUrl(String baseUrl) {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        return remoteUrl(URI.create(baseUrl));
    }

    /**
     * Sets the snapshot path.
     *
     * @param snapshotPath the path; non-null
     * @return this builder
     */
    public HybridFlagProviderBuilder snapshotPath(Path snapshotPath) {
        this.snapshotPath = Objects.requireNonNull(snapshotPath, "snapshotPath must not be null");
        return this;
    }

    /**
     * Sets the snapshot path from a string.
     *
     * @param snapshotPath the path string; non-null
     * @return this builder
     */
    public HybridFlagProviderBuilder snapshotPath(String snapshotPath) {
        Objects.requireNonNull(snapshotPath, "snapshotPath must not be null");
        this.snapshotPath = Paths.get(snapshotPath);
        return this;
    }

    /**
     * Sets the snapshot serialization format.
     *
     * @param format the format; non-null
     * @return this builder
     */
    public HybridFlagProviderBuilder snapshotFormat(SnapshotFormat format) {
        this.snapshotFormat = Objects.requireNonNull(format, "format must not be null");
        return this;
    }

    /**
     * Sets whether to watch the snapshot file for manual edits.
     *
     * @param watchSnapshot {@code true} to enable watching
     * @return this builder
     */
    public HybridFlagProviderBuilder watchSnapshot(boolean watchSnapshot) {
        this.watchSnapshot = watchSnapshot;
        return this;
    }

    /**
     * Sets the debounce window for ignoring self-induced file events.
     *
     * @param debounce the debounce duration; non-null, must be positive
     * @return this builder
     */
    public HybridFlagProviderBuilder snapshotDebounce(Duration debounce) {
        this.snapshotDebounce = Objects.requireNonNull(debounce, "debounce must not be null");
        return this;
    }

    /**
     * Sets whether to fail initialization when neither remote nor snapshot can produce data.
     *
     * @param failIfNoFallback {@code true} to fail strictly
     * @return this builder
     */
    public HybridFlagProviderBuilder failIfNoFallback(boolean failIfNoFallback) {
        this.failIfNoFallback = failIfNoFallback;
        return this;
    }

    /**
     * Builds the provider.
     *
     * @return a configured but un-initialized {@link HybridFlagProvider}.
     *         Caller must invoke {@link HybridFlagProvider#init()}.
     * @throws IllegalStateException if {@code remoteConfig} or {@code snapshotPath} were not set
     */
    public HybridFlagProvider build() {
        if (remoteConfig == null) {
            throw new IllegalStateException("remoteConfig must be set before building");
        }
        if (snapshotPath == null) {
            throw new IllegalStateException("snapshotPath must be set before building");
        }
        HybridProviderConfig cfg = new HybridProviderConfig(
                remoteConfig, snapshotPath, snapshotFormat,
                watchSnapshot, snapshotDebounce, failIfNoFallback);
        return new HybridFlagProvider(cfg);
    }
}
