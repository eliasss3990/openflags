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
 *                        successful remote change; non-null; must not be a directory itself.
 *                        The parent directory is created automatically during
 *                        {@link HybridFlagProvider#init()} if it does not exist yet
 * @param snapshotFormat  serialization format for the snapshot; non-null; default {@link SnapshotFormat#JSON}
 * @param watchSnapshot   whether to enable filesystem watching on the snapshot path so that manual
 *                        edits trigger reload while the remote is in {@code ERROR}/{@code NOT_READY};
 *                        default {@code true}
 * @param snapshotDebounce window during which file change events are treated as echoes of the
 *                        provider's own snapshot writes and ignored; must be positive and
 *                        {@code <= remoteConfig.pollInterval() / 2}; default {@code 500ms}
 * @param failIfNoFallback if {@code true}, {@code init()} throws {@link com.openflags.core.exception.ProviderException}
 *                        when both the remote and the file initialization fail; if
 *                        {@code false} (default), {@code init()} succeeds and the provider
 *                        starts in {@code ERROR} state — {@link com.openflags.provider.hybrid.HybridFlagProvider#getFlag(String)}
 *                        will return {@link java.util.Optional#empty()} until a source recovers.
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
     * @throws IllegalArgumentException if {@code snapshotPath} is itself a directory; or if
     *                                  {@code snapshotDebounce} is non-positive or
     *                                  {@code > remoteConfig.pollInterval() / 2}
     */
    public HybridProviderConfig {
        Objects.requireNonNull(remoteConfig, "remoteConfig must not be null");
        Objects.requireNonNull(snapshotPath, "snapshotPath must not be null");
        if (Files.isDirectory(snapshotPath)) {
            throw new IllegalArgumentException(
                    "snapshotPath must not be a directory: " + snapshotPath);
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
        Duration halfPoll = remoteConfig.pollInterval().dividedBy(2);
        if (snapshotDebounce.compareTo(halfPoll) > 0) {
            throw new IllegalArgumentException(
                    "snapshotDebounce (" + snapshotDebounce + ") must be <= remoteConfig.pollInterval/2 ("
                            + halfPoll + ")");
        }
    }
}
