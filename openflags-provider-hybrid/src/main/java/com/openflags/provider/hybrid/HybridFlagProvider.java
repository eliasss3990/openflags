package com.openflags.provider.hybrid;

import com.openflags.core.event.FlagChangeEvent;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.metrics.MetricsRecorder;
import com.openflags.core.model.Flag;
import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderDiagnostics;
import com.openflags.core.provider.ProviderState;
import com.openflags.provider.file.FileFlagProvider;
import com.openflags.provider.remote.RemoteFlagProvider;
import com.openflags.provider.remote.RemoteFlagProviderBuilder;
import com.openflags.provider.remote.RemotePollListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link FlagProvider} that combines a {@link RemoteFlagProvider}
 * (primary source) with a {@link FileFlagProvider} (fallback) and
 * persists each successful remote update to a local snapshot file.
 *
 * <h2>Initialization</h2>
 * <ul>
 * <li>The provider first attempts to initialize the remote provider.</li>
 * <li>If the remote initialization fails, it falls back to the file provider
 * reading
 * the snapshot path; if that also fails, {@code init()} throws
 * {@link ProviderException}.</li>
 * <li>If the remote succeeds, the file provider is initialized lazily as a
 * watcher for
 * manual edits; if the snapshot file does not exist yet, file initialization is
 * deferred until the first snapshot is written.</li>
 * </ul>
 *
 * <h2>Routing</h2>
 * <p>
 * {@link #getFlag(String)} routes to the remote provider when its state is
 * {@link ProviderState#READY} or {@link ProviderState#DEGRADED}; otherwise it
 * routes to
 * the file provider.
 * </p>
 *
 * <h2>Snapshot writing</h2>
 * <p>
 * On each remote change event, the entire {@code Map<String, Flag>} is
 * serialized to
 * the configured snapshot path using a write-to-temp + atomic rename strategy.
 * </p>
 *
 * <h2>Behavior when both providers are unavailable</h2>
 * <p>
 * If both the remote and the file provider are in {@code NOT_READY} or
 * {@code ERROR},
 * {@link #getFlag(String)} delegates to the file provider, which will return
 * {@link Optional#empty()}. The hybrid provider does not throw on read;
 * consumers must
 * handle the empty result. {@link #getState()} reports {@code ERROR} in this
 * case (or
 * {@code NOT_READY} if neither provider has been initialized yet).
 * </p>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * Thread-safe. Concurrency model:
 * </p>
 * <ul>
 * <li>{@code init()} and {@code shutdown()} are {@code synchronized} on the
 * provider
 * instance to prevent concurrent lifecycle transitions.</li>
 * <li>{@code initialized} and {@code shutdown} are {@code volatile} so that
 * {@code requireInitialized()} / {@code requireNotShutdown()} (called from
 * {@code getFlag()} outside any lock) observe a consistent state.</li>
 * <li>{@code lastSnapshotWriteAt} is {@code volatile}; written by the remote
 * poll thread only after a successful atomic rename. The {@code AtomicBoolean}
 * {@code expectingSelfWrite} is set <em>before</em> the rename and consumed by
 * the first {@code onFileChange} invocation, ensuring our own self-write
 * events are filtered even when the OS event races ahead of the post-write
 * bookkeeping.</li>
 * <li>Read paths ({@code getFlag}, {@code getAllFlags}, {@code getState}) are
 * lock-free
 * and delegate to the underlying providers, whose own snapshots are
 * atomic.</li>
 * <li>Listener lists use {@link CopyOnWriteArrayList}; iteration runs outside
 * any lock.</li>
 * </ul>
 */
public final class HybridFlagProvider implements FlagProvider, ProviderDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(HybridFlagProvider.class);

    private final HybridProviderConfig config;
    private final RemoteFlagProvider remote;
    private final FileFlagProvider file;
    private final SnapshotWriter snapshotWriter;
    private final List<FlagChangeListener> publicListeners = new CopyOnWriteArrayList<>();

    /** Counts consecutive snapshot write failures for log throttling. */
    private final AtomicInteger consecutiveWriteFailures = new AtomicInteger(0);

    /**
     * Tracks last successful snapshot write time; used by onFileChange debounce.
     */
    private volatile Instant lastSnapshotWriteAt = Instant.EPOCH;

    /**
     * Set immediately before invoking {@link SnapshotWriter#write} so that the
     * filesystem watcher event triggered by our own atomic rename is filtered
     * out by {@link #onFileChange}, even if the OS event arrives before the
     * post-write timestamp assignment. The flag uses consume-once semantics:
     * the first {@code onFileChange} invocation while it is {@code true}
     * clears it. Cleared as well after a write failure so that subsequent
     * external events are not silenced.
     */
    private final AtomicBoolean expectingSelfWrite = new AtomicBoolean(false);

    /**
     * Current routing target ("remote" or "file"); detects transitions for
     * hybrid.fallback metric.
     */
    private final AtomicReference<String> routingTarget = new AtomicReference<>("remote");

    /**
     * Optional metrics recorder; defaults to NOOP. Wired by the Spring Boot
     * starter.
     */
    private volatile MetricsRecorder metricsRecorder = MetricsRecorder.NOOP;

    // volatile: read in requireInitialized() which is called from getFlag() outside
    // synchronized
    private volatile boolean initialized = false;
    private volatile boolean shutdown = false;

    /**
     * Use {@link HybridFlagProviderBuilder} (via {@link #builder()}) to construct
     * instances.
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
                .userAgent(config.remoteConfig().userAgent())
                .failureThreshold(config.remoteConfig().failureThreshold())
                .maxBackoff(config.remoteConfig().maxBackoff());
        if (config.remoteConfig().authHeaderName() != null) {
            remoteBuilder.apiKey(config.remoteConfig().authHeaderName(),
                    config.remoteConfig().authHeaderValue());
        }
        this.remote = remoteBuilder.build();
        this.remote.setPollListener(this::onPollComplete);
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
        this.remote.setPollListener(this::onPollComplete);
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
        if (initialized)
            return;

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
        return resolveActiveProvider().getFlag(key);
    }

    @Override
    public Map<String, Flag> getAllFlags() {
        requireInitialized();
        requireNotShutdown();
        return resolveActiveProvider().getAllFlags();
    }

    /**
     * Returns the combined state. See {@code 02-modelo-hybrid.md §4} for the table.
     */
    @Override
    public ProviderState getState() {
        if (shutdown)
            return ProviderState.SHUTDOWN;
        if (!initialized)
            return ProviderState.NOT_READY;
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
     * Shuts down the inner providers in reverse order of initialization.
     * Idempotent.
     */
    @Override
    public synchronized void shutdown() {
        if (shutdown)
            return;
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

    private void onPollComplete(Map<String, Flag> flagSnapshot) {
        writeSafe(flagSnapshot);
    }

    private void onRemoteChange(FlagChangeEvent event) {
        for (FlagChangeListener listener : publicListeners) {
            try {
                listener.onFlagChange(event);
            } catch (Throwable t) {
                log.warn("FlagChangeListener threw an exception", t);
            }
        }
    }

    private void onFileChange(FlagChangeEvent event) {
        if (expectingSelfWrite.compareAndSet(true, false)) {
            log.debug("HybridFlagProvider: ignoring file event from our own snapshot write");
            return;
        }
        Duration sinceLastWrite = Duration.between(lastSnapshotWriteAt, Instant.now());
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
        // Mark the impending self-write before invoking the writer so that an
        // OS file event arriving before this thread reaches the post-write
        // bookkeeping is still filtered out by onFileChange.
        expectingSelfWrite.set(true);
        long startNanos = System.nanoTime();
        boolean success = false;
        try {
            snapshotWriter.write(flags, config.snapshotPath());
            success = true;
            long durationNanos = System.nanoTime() - startNanos;
            // Update the timestamp only after a successful write so that the
            // debounce window in onFileChange reflects real snapshots and a
            // failed self-write does not silence subsequent external events.
            lastSnapshotWriteAt = Instant.now();
            consecutiveWriteFailures.set(0);
            try {
                metricsRecorder.recordSnapshotWrite("success", durationNanos);
            } catch (Throwable t) {
                log.warn("MetricsRecorder.recordSnapshotWrite threw", t);
            }
        } catch (IOException e) {
            long durationNanos = System.nanoTime() - startNanos;
            int failures = consecutiveWriteFailures.incrementAndGet();
            // log at power-of-two milestones only
            if (Integer.bitCount(failures) == 1) {
                log.warn("HybridFlagProvider: snapshot write failed (consecutive={}): {}",
                        failures, e.getMessage());
            }
            try {
                metricsRecorder.recordSnapshotWrite("failure", durationNanos);
            } catch (Throwable t) {
                log.warn("MetricsRecorder.recordSnapshotWrite threw", t);
            }
        } finally {
            // If the write did not complete (IOException or any unchecked
            // throwable) there will be no self-event to filter: clear the
            // flag so the next legitimate file event is not silenced.
            if (!success) {
                expectingSelfWrite.set(false);
            }
        }
    }

    /**
     * Resolves the active sub-provider for the current request, updates
     * {@link #routingTarget}, and emits a {@code hybrid.fallback} metric on
     * every transition. <strong>Has side-effects</strong>: never call from
     * read-only getters like {@code getState()} or {@code flagCount()};
     * those must read {@code routingTarget} directly.
     */
    private FlagProvider resolveActiveProvider() {
        ProviderState rs = remote.getState();
        String target = (rs == ProviderState.READY || rs == ProviderState.DEGRADED)
                ? "remote"
                : "file";
        String prev = routingTarget.getAndSet(target);
        if (!prev.equals(target)) {
            try {
                metricsRecorder.recordHybridFallback(prev, target);
            } catch (Throwable t) {
                log.warn("MetricsRecorder.recordHybridFallback threw", t);
            }
        }
        return target.equals("remote") ? remote : file;
    }

    /**
     * Wires a {@link MetricsRecorder} that receives {@code hybrid.fallback}
     * events on every routing transition. Defaults to {@link MetricsRecorder#NOOP}.
     *
     * @param recorder the recorder; non-null
     */
    public void setMetricsRecorder(MetricsRecorder recorder) {
        this.metricsRecorder = Objects.requireNonNull(recorder, "recorder must not be null");
    }

    @Override
    public String providerType() {
        return "hybrid";
    }

    @Override
    public Map<String, Object> diagnostics() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hybrid.routing_target", routingTarget.get());
        data.put("hybrid.snapshot_path", config.snapshotPath().toString());
        Instant write = lastSnapshotWriteAt;
        long ageSec = write.equals(Instant.EPOCH)
                ? -1L
                : Duration.between(write, Instant.now()).toSeconds();
        data.put("hybrid.snapshot_age_seconds", ageSec);
        data.put("hybrid.last_snapshot_write",
                write.equals(Instant.EPOCH) ? "" : write.toString());
        if (remote instanceof ProviderDiagnostics rd) {
            data.putAll(rd.diagnostics());
        }
        if (file instanceof ProviderDiagnostics fd) {
            data.putAll(fd.diagnostics());
        }
        return Collections.unmodifiableMap(data);
    }

    @Override
    public Instant lastUpdate() {
        Instant r = (remote instanceof ProviderDiagnostics rd) ? rd.lastUpdate() : null;
        Instant f = (file instanceof ProviderDiagnostics fd) ? fd.lastUpdate() : null;
        if (r == null)
            return f;
        if (f == null)
            return r;
        return r.isAfter(f) ? r : f;
    }

    @Override
    public int flagCount() {
        if (shutdown)
            return 0;
        // Read routingTarget directly; resolveActiveProvider() has side-effects
        // (metric emission) and must not be invoked from a read-only getter.
        // Value may be one transition stale — acceptable for diagnostics.
        // Both `remote` and `file` are non-null final fields validated by the builder;
        // Objects.requireNonNull makes the contract explicit for static analyzers.
        FlagProvider active = Objects.requireNonNull(
                "remote".equals(routingTarget.get()) ? remote : file,
                "active provider must not be null");
        if (active instanceof ProviderDiagnostics d) {
            return d.flagCount();
        }
        return active.getAllFlags().size();
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
