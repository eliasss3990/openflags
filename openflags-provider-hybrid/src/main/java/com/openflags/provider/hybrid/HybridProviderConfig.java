package com.openflags.provider.hybrid;

import com.openflags.provider.remote.RemoteProviderConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for a {@link HybridFlagProvider}.
 *
 * @param remoteConfig    configuration for the inner {@link com.openflags.provider.remote.RemoteFlagProvider};
 *                        non-null
 * @param snapshotPath    filesystem path where the snapshot is read at startup and written on each
 *                        successful remote change; non-null; parent directory must exist
 * @param snapshotFormat  serialization format for the snapshot; non-null; default {@link SnapshotFormat#JSON}
 * @param watchSnapshot   whether to enable filesystem watching on the snapshot path so that manual
 *                        edits trigger reload while the remote is in {@code ERROR}/{@code NOT_READY};
 *                        default {@code true}
 * @param snapshotDebounce window during which file change events are treated as echoes of the
 *                        provider's own snapshot writes and ignored; must be positive and strictly
 *                        less than {@code remoteConfig.pollInterval()}; default {@code 500ms}
 * @param failIfNoFallback if {@code true}, {@code init()} fails when the remote initialization
 *                        fails and the snapshot file does not exist or cannot be parsed; if
 *                        {@code false} (default), the same condition is allowed but the provider
 *                        starts in {@code ERROR} state if neither source produced data
 */
public record HybridProviderConfig(
        RemoteProviderConfig remoteConfig,
        Path snapshotPath,
        SnapshotFormat snapshotFormat,
        boolean watchSnapshot,
        Duration snapshotDebounce,
        boolean failIfNoFallback
) {

    /** Default debounce window for ignoring self-induced file events. */
    public static final Duration DEFAULT_SNAPSHOT_DEBOUNCE = Duration.ofMillis(500);

    /**
     * Compact constructor that validates the fields and applies defaults.
     *
     * @throws NullPointerException     if any required field is null
     * @throws IllegalArgumentException if {@code snapshotPath} is a directory or its parent
     *                                  directory does not exist; or if {@code snapshotDebounce}
     *                                  is non-positive or {@code >= remoteConfig.pollInterval()}
     */
    public HybridProviderConfig {
        Objects.requireNonNull(remoteConfig, "remoteConfig must not be null");
        Objects.requireNonNull(snapshotPath, "snapshotPath must not be null");
        if (Files.isDirectory(snapshotPath)) {
            throw new IllegalArgumentException(
                    "snapshotPath must not be a directory: " + snapshotPath);
        }
        Path parent = snapshotPath.toAbsolutePath().getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalArgumentException(
                    "snapshotPath parent directory must exist: " + parent);
        }
        if (snapshotFormat == null) {
            snapshotFormat = SnapshotFormat.JSON;
        }
        if (snapshotDebounce == null) {
            snapshotDebounce = DEFAULT_SNAPSHOT_DEBOUNCE;
        }
        if (snapshotDebounce.isNegative() || snapshotDebounce.isZero()) {
            throw new IllegalArgumentException("snapshotDebounce must be positive");
        }
        if (snapshotDebounce.compareTo(remoteConfig.pollInterval()) >= 0) {
            throw new IllegalArgumentException(
                    "snapshotDebounce (" + snapshotDebounce + ") must be < remoteConfig.pollInterval ("
                            + remoteConfig.pollInterval() + ")");
        }
    }
}
