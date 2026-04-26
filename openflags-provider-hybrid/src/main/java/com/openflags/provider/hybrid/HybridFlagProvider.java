package com.openflags.provider.hybrid;

import com.openflags.core.event.FlagChangeEvent;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.model.Flag;
import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderState;
import com.openflags.provider.file.FileFlagProvider;
import com.openflags.provider.remote.RemoteFlagProvider;
import com.openflags.provider.remote.RemoteFlagProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link FlagProvider} that combines a {@link RemoteFlagProvider}
 * (primary source) with a {@link FileFlagProvider} (fallback) and
 * persists each successful remote update to a local snapshot file.
 *
 * <h2>Initialization</h2>
 * <ul>
 *   <li>The provider first attempts to initialize the remote provider.</li>
 *   <li>If the remote initialization fails, it falls back to the file provider reading
 *       the snapshot path; if that also fails, {@code init()} throws
 *       {@link ProviderException}.</li>
 *   <li>If the remote succeeds, the file provider is initialized lazily as a watcher for
 *       manual edits; if the snapshot file does not exist yet, file initialization is
 *       deferred until the first snapshot is written.</li>
 * </ul>
 *
 * <h2>Routing</h2>
 * <p>{@link #getFlag(String)} routes to the remote provider when its state is
 * {@link ProviderState#READY} or {@link ProviderState#DEGRADED}; otherwise it routes to
 * the file provider.</p>
 *
 * <h2>Snapshot writing</h2>
 * <p>On each remote change event, the entire {@code Map<String, Flag>} is serialized to
 * the configured snapshot path using a write-to-temp + atomic rename strategy.</p>
 *
 * <h2>Behavior when both providers are unavailable</h2>
 * <p>If both the remote and the file provider are in {@code NOT_READY} or {@code ERROR},
 * {@link #getFlag(String)} delegates to the file provider, which will return
 * {@link Optional#empty()}. The hybrid provider does not throw on read; consumers must
 * handle the empty result. {@link #getState()} reports {@code ERROR} in this case (or
 * {@code NOT_READY} if neither provider has been initialized yet).</p>
 *
 * <h2>Thread-safety</h2>
 * <p>Thread-safe. {@code init()} and {@code shutdown()} are {@code synchronized}; reads
 * are lock-free.</p>
 */
public final class HybridFlagProvider implements FlagProvider {

    private static final Logger log = LoggerFactory.getLogger(HybridFlagProvider.class);
    private static final Duration SNAPSHOT_WRITE_COALESCE_WINDOW = Duration.ofMillis(50);

    private final HybridProviderConfig config;
    private final RemoteFlagProvider remote;
    private final FileFlagProvider file;
    private final SnapshotWriter snapshotWriter;
    private final List<FlagChangeListener> publicListeners = new CopyOnWriteArrayList<>();

    /** Tracks the last time the snapshot was successfully written (for debounce). */
    private volatile Instant lastSnapshotWriteAt = Instant.EPOCH;

    /** Counts consecutive snapshot write failures for log throttling. */
    private final AtomicInteger consecutiveWriteFailures = new AtomicInteger(0);

    private boolean initialized = false;
    private volatile boolean shutdown = false;

    /**
     * Use {@link HybridFlagProviderBuilder} (via {@link #builder()}) to construct instances.
     *
     * @param config the immutable configuration; non-null
     */
    HybridFlagProvider(HybridProviderConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        RemoteFlagProviderBuilder remoteBuilder = RemoteFlagProviderBuilder
                .forUrl(config.remoteConfig().baseUrl())
                .flagsPath(config.remoteConfig().flagsPath())
                .connectTimeout(config.remoteConfig().connectTimeout())
                .requestTimeout(config.remoteConfig().requestTimeout())
                .pollInterval(config.remoteConfig().pollInterval())
                .cacheTtl(config.remoteConfig().cacheTtl())
                .userAgent(config.remoteConfig().userAgent());
        if (config.remoteConfig().authHeaderName() != null) {
            remoteBuilder.apiKey(config.remoteConfig().authHeaderName(),
                    config.remoteConfig().authHeaderValue());
        }
        this.remote = remoteBuilder.build();
        this.file = FileFlagProvider.builder()
                .path(config.snapshotPath())
                .watchEnabled(config.watchSnapshot())
                .build();
        this.snapshotWriter = new SnapshotWriter(config.snapshotFormat());
    }

    /**
     * Package-private constructor for testing; allows injecting mock providers.
     */
    HybridFlagProvider(HybridProviderConfig config,
                       RemoteFlagProvider remote,
                       FileFlagProvider file,
                       SnapshotWriter snapshotWriter) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.remote = Objects.requireNonNull(remote, "remote must not be null");
        this.file = Objects.requireNonNull(file, "file must not be null");
        this.snapshotWriter = Objects.requireNonNull(snapshotWriter, "snapshotWriter must not be null");
    }

    /**
     * Creates a new builder.
     *
     * @return a fresh builder
     */
    public static HybridFlagProviderBuilder builder() {
        return new HybridFlagProviderBuilder();
    }

    /**
     * Initializes the underlying providers per the documented sequence.
     * Idempotent; subsequent calls after a successful init are no-ops.
     *
     * @throws ProviderException if both the remote and the file initialization fail
     */
    @Override
    public synchronized void init() {
        requireNotShutdown();
        if (initialized) return;

        boolean remoteOk = false;
        boolean fileOk = false;

        // register internal listeners before starting so no events are missed
        remote.addChangeListener(this::onRemoteChange);
        file.addChangeListener(this::onFileChange);

        try {
            remote.init();
            remoteOk = true;
            log.info("HybridFlagProvider: remote provider initialized successfully");
        } catch (ProviderException e) {
            log.warn("HybridFlagProvider: remote provider init failed: {}", e.getMessage());
        }

        try {
            file.init();
            fileOk = true;
            log.info("HybridFlagProvider: file provider initialized successfully");
        } catch (ProviderException e) {
            if (remoteOk) {
                log.info("HybridFlagProvider: snapshot file not yet available (will be written on first remote poll)");
            } else {
                log.warn("HybridFlagProvider: file provider init failed: {}", e.getMessage());
            }
        }

        if (!remoteOk && !fileOk) {
            if (config.failIfNoFallback()) {
                throw new ProviderException(
                        "HybridFlagProvider: both remote and file providers failed to initialize");
            }
            log.warn("HybridFlagProvider: both providers failed to initialize; "
                    + "starting in degraded state (failIfNoFallback=false). "
                    + "getFlag() will return Optional.empty() until a source recovers.");
        }

        initialized = true;
        log.info("HybridFlagProvider initialized (remote={}, file={})", remoteOk, fileOk);
    }

    @Override
    public Optional<Flag> getFlag(String key) {
        Objects.requireNonNull(key, "key must not be null");
        requireInitialized();
        requireNotShutdown();
        return activeProvider().getFlag(key);
    }

    @Override
    public Map<String, Flag> getAllFlags() {
        requireInitialized();
        requireNotShutdown();
        return activeProvider().getAllFlags();
    }

    /**
     * Returns the combined state. See {@code 02-modelo-hybrid.md §4} for the table.
     */
    @Override
    public ProviderState getState() {
        if (shutdown) return ProviderState.SHUTDOWN;
        if (!initialized) return ProviderState.NOT_READY;
        ProviderState rs = remote.getState();
        ProviderState fs = file.getState();

        if (rs == ProviderState.SHUTDOWN || fs == ProviderState.SHUTDOWN) {
            return ProviderState.SHUTDOWN;
        }
        if (rs == ProviderState.READY) {
            return ProviderState.READY;
        }
        if (rs == ProviderState.DEGRADED) {
            return ProviderState.DEGRADED;
        }
        // remote is NOT_READY or ERROR
        if (fs == ProviderState.READY || fs == ProviderState.DEGRADED) {
            return ProviderState.DEGRADED;
        }
        // remote=ERROR or NOT_READY, file=ERROR or NOT_READY (initialized → ERROR)
        return ProviderState.ERROR;
    }

    @Override
    public void addChangeListener(FlagChangeListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        publicListeners.add(listener);
    }

    @Override
    public void removeChangeListener(FlagChangeListener listener) {
        publicListeners.remove(listener);
    }

    /**
     * Shuts down the inner providers in reverse order of initialization. Idempotent.
     */
    @Override
    public synchronized void shutdown() {
        if (shutdown) return;
        shutdown = true;

        try {
            remote.shutdown();
        } catch (Exception e) {
            log.warn("HybridFlagProvider: error shutting down remote provider", e);
        }
        try {
            file.shutdown();
        } catch (Exception e) {
            log.warn("HybridFlagProvider: error shutting down file provider", e);
        }
        publicListeners.clear();
        log.info("HybridFlagProvider shut down");
    }

    // ---- internal listeners ----

    private void onRemoteChange(FlagChangeEvent event) {
        Instant now = Instant.now();
        Duration sinceLastWrite = Duration.between(lastSnapshotWriteAt, now);
        if (sinceLastWrite.compareTo(SNAPSHOT_WRITE_COALESCE_WINDOW) >= 0) {
            writeSafe(remote.getAllFlags());
        } else {
            log.debug("HybridFlagProvider: skipping snapshot write within coalesce window");
        }
        for (FlagChangeListener listener : publicListeners) {
            try {
                listener.onFlagChange(event);
            } catch (Throwable t) {
                log.warn("FlagChangeListener threw an exception", t);
            }
        }
    }

    private void onFileChange(FlagChangeEvent event) {
        Instant now = Instant.now();
        Duration sinceLastWrite = Duration.between(lastSnapshotWriteAt, now);
        if (sinceLastWrite.compareTo(config.snapshotDebounce()) < 0) {
            log.debug("HybridFlagProvider: ignoring file event within debounce window ({} ms)",
                    config.snapshotDebounce().toMillis());
            return;
        }
        ProviderState rs = remote.getState();
        if (rs == ProviderState.ERROR || rs == ProviderState.NOT_READY) {
            for (FlagChangeListener listener : publicListeners) {
                try {
                    listener.onFlagChange(event);
                } catch (Throwable t) {
                    log.warn("FlagChangeListener threw an exception", t);
                }
            }
        } else {
            log.debug("HybridFlagProvider: file changed but remote is active ({}); not republishing", rs);
        }
    }

    private void writeSafe(Map<String, Flag> flags) {
        try {
            snapshotWriter.write(flags, config.snapshotPath());
            consecutiveWriteFailures.set(0);
            lastSnapshotWriteAt = Instant.now();
        } catch (IOException e) {
            int failures = consecutiveWriteFailures.incrementAndGet();
            // log at power-of-two milestones only
            if (Integer.bitCount(failures) == 1) {
                log.warn("HybridFlagProvider: snapshot write failed (consecutive={}): {}",
                        failures, e.getMessage());
            }
        }
    }

    private FlagProvider activeProvider() {
        ProviderState rs = remote.getState();
        if (rs == ProviderState.READY || rs == ProviderState.DEGRADED) {
            return remote;
        }
        return file;
    }

    private void requireInitialized() {
        if (!initialized) {
            throw new IllegalStateException("HybridFlagProvider has not been initialized");
        }
    }

    private void requireNotShutdown() {
        if (shutdown) {
            throw new IllegalStateException("HybridFlagProvider has been shut down");
        }
    }
}
