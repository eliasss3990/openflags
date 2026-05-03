package com.openflags.provider.hybrid;

import com.openflags.core.event.FlagChangeEvent;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.metrics.MetricsRecorder;
import com.openflags.core.metrics.OpenFlagsMetrics;
import com.openflags.core.model.Flag;
import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderDiagnostics;
import com.openflags.core.provider.ProviderState;
import com.openflags.provider.file.FileFlagProvider;
import com.openflags.provider.remote.MetricsRecordingPollListener;
import com.openflags.provider.remote.RemoteFlagProvider;
import com.openflags.provider.remote.RemoteFlagProviderBuilder;
import com.openflags.provider.remote.RemotePollListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * Nanos at which the hybrid provider entered fallback mode; used to compute
     * {@link com.openflags.core.metrics.OpenFlagsMetrics.Names#HYBRID_FALLBACK_DURATION}.
     * Zero means fallback is not active.
     */
    private volatile long fallbackStartNanos = 0L;

    /** Whether fallback is currently active; tracks state for the gauge. */
    private final AtomicBoolean fallbackActive = new AtomicBoolean(false);

    /**
     * Last observed combined {@link ProviderState}; used to detect transitions and
     * emit {@link com.openflags.core.metrics.OpenFlagsMetrics.Names#HYBRID_STATE_TRANSITIONS}.
     */
    private final AtomicReference<ProviderState> lastObservedState = new AtomicReference<>(ProviderState.NOT_READY);

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

        ensureSnapshotParentExists(config.snapshotPath());

        boolean remoteOk = false;
        boolean fileOk = false;

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

        // Register internal listeners only after init() so that the initial
        // load (which surfaces every flag as CREATED on remote's first poll
        // and may also fire a self-write event on file) is not forwarded to
        // public listeners as if it were a runtime change.
        remote.addChangeListener(this::onRemoteChange);
        file.addChangeListener(this::onFileChange);
        // If a synchronous first poll already triggered writeSafe before the
        // file listener was registered, expectingSelfWrite may still be true
        // with no consumer. Disarm it so the next legitimate file event is
        // not silenced; lastSnapshotWriteAt still guards the debounce window.
        expectingSelfWrite.set(false);

        initialized = true;
        log.info("HybridFlagProvider initialized (remote={}, file={})", remoteOk, fileOk);
    }

    @Override
    public Optional<Flag> getFlag(String key) {
        Objects.requireNonNull(key, "key must not be null");
        requireInitialized();
        requireNotShutdown();
        FlagProvider active = resolveActiveProvider();
        String source = (active == remote) ? "primary" : "fallback";
        long start = System.nanoTime();
        try {
            return active.getFlag(key);
        } finally {
            recordEvaluationLatencySafely(source, System.nanoTime() - start);
        }
    }

    private void recordEvaluationLatencySafely(String source, long durationNanos) {
        try {
            metricsRecorder.recordHybridEvaluationLatency(source, durationNanos);
        } catch (Exception e) {
            log.warn("MetricsRecorder.recordHybridEvaluationLatency threw", e);
        }
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
            } catch (Exception e) {
                log.warn("FlagChangeListener threw an exception", e);
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
                } catch (Exception e) {
                    log.warn("FlagChangeListener threw an exception", e);
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
            recordSnapshotWriteSafely("success", durationNanos);
        } catch (IOException e) {
            long durationNanos = System.nanoTime() - startNanos;
            int failures = consecutiveWriteFailures.incrementAndGet();
            // log at power-of-two milestones only
            if (Integer.bitCount(failures) == 1) {
                log.warn("HybridFlagProvider: snapshot write failed (consecutive={}): {}",
                        failures, e.getMessage());
            }
            recordSnapshotWriteSafely("failure", durationNanos);
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
     * {@link #routingTarget}, and emits fallback and state-transition metrics on
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
            recordHybridFallbackSafely(prev, target, rs);
        }
        recordStateTransitionSafely();
        return target.equals("remote") ? remote : file;
    }

    private void recordSnapshotWriteSafely(String outcome, long durationNanos) {
        try {
            metricsRecorder.recordSnapshotWrite(outcome, durationNanos);
        } catch (Exception e) {
            log.warn("MetricsRecorder.recordSnapshotWrite threw", e);
        }
    }

    private void recordHybridFallbackSafely(String from, String to, ProviderState remoteState) {
        try {
            metricsRecorder.recordHybridFallback(from, to);
            if ("file".equals(to)) {
                // Transitioning to fallback: activation
                String cause = fallbackCause(remoteState);
                metricsRecorder.recordHybridFallbackActivation(cause);
                fallbackActive.set(true);
                fallbackStartNanos = System.nanoTime();
            } else {
                // Transitioning back to primary: deactivation
                long durationNanos = fallbackStartNanos > 0 ? System.nanoTime() - fallbackStartNanos : 0L;
                metricsRecorder.recordHybridFallbackDeactivation(durationNanos);
                fallbackActive.set(false);
                fallbackStartNanos = 0L;
            }
        } catch (Exception e) {
            log.warn("MetricsRecorder hybrid fallback recording threw", e);
        }
    }

    private void recordStateTransitionSafely() {
        try {
            ProviderState current = getState();
            ProviderState prev = lastObservedState.getAndSet(current);
            if (prev != current) {
                metricsRecorder.recordHybridStateTransition(prev.name(), current.name());
            }
        } catch (Exception e) {
            log.warn("MetricsRecorder hybrid state transition recording threw", e);
        }
    }

    /**
     * Maps the current remote {@link ProviderState} to a bounded cause string for
     * the fallback-activation metric tag.
     */
    private static String fallbackCause(ProviderState remoteState) {
        return switch (remoteState) {
            case ERROR -> "primary_error";
            case NOT_READY -> "primary_not_ready";
            case STALE -> "primary_state_error";
            default -> "primary_state_error";
        };
    }

    /**
     * Wires a {@link MetricsRecorder} that receives hybrid metrics on every
     * routing transition, poll cycle, and state change.
     * Defaults to {@link MetricsRecorder#NOOP}.
     *
     * <p>Composing a {@link MetricsRecordingPollListener} on top of the existing
     * internal poll listener ensures that {@code openflags.poll.success} /
     * {@code openflags.poll.failure} counters and {@code openflags.poll.latency}
     * timers are emitted for hybrid providers in the same way they are for
     * standalone remote providers.
     *
     * @param recorder the recorder; non-null
     */
    public void setMetricsRecorder(MetricsRecorder recorder) {
        this.metricsRecorder = Objects.requireNonNull(recorder, "recorder must not be null");

        // Compose a MetricsRecordingPollListener on top of the existing internal
        // poll listener so that openflags.poll.* meters are emitted for hybrid
        // providers (parity with standalone remote provider wiring in the starter).
        MetricsRecordingPollListener pollMetrics = new MetricsRecordingPollListener(recorder);
        RemotePollListener existing = this::onPollComplete;
        remote.setPollListener(new ComposedPollListener(existing, pollMetrics));

        // Register gauges whose value is read on demand.
        recorder.registerGauge(
                OpenFlagsMetrics.Names.HYBRID_FALLBACK_ACTIVE,
                Collections.emptyList(),
                () -> fallbackActive.get() ? 1 : 0);
        recorder.registerGauge(
                OpenFlagsMetrics.Names.HYBRID_STATE_CURRENT,
                Collections.emptyList(),
                () -> providerStateCode(getState()));
    }

    /**
     * Composes two {@link RemotePollListener}s; both are invoked in order.
     * Exceptions from the first do not prevent the second from being called.
     */
    private static final class ComposedPollListener implements RemotePollListener {
        private final RemotePollListener primary;
        private final RemotePollListener secondary;

        ComposedPollListener(RemotePollListener primary, RemotePollListener secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }

        @Override
        public void onPollComplete(Map<String, Flag> snapshot) {
            primary.onPollComplete(snapshot);
            secondary.onPollComplete(snapshot);
        }

        @Override
        public void onPollOutcome(String outcome, long durationNanos) {
            primary.onPollOutcome(outcome, durationNanos);
            secondary.onPollOutcome(outcome, durationNanos);
        }
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

    private static void ensureSnapshotParentExists(Path snapshotPath) {
        Path parent = snapshotPath.toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        if (!Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
                log.info("HybridFlagProvider: created snapshot parent directory: {}", parent);
            } catch (IOException e) {
                throw new ProviderException(
                        "Cannot create snapshot parent directory '" + parent + "': " + e.getMessage(), e);
            }
        } else if (!Files.isDirectory(parent)) {
            throw new ProviderException(
                    "Snapshot parent path exists but is not a directory: " + parent);
        }
    }

    /**
     * Stable, non-ordinal mapping of {@link ProviderState} to a numeric code
     * suitable for a gauge. Mirrors
     * {@code MicrometerMetricsRecorder#providerStateCode} without creating a
     * compile-time dependency on Micrometer.
     *
     * @param state state to map; {@code null} maps to {@code -1}
     * @return numeric code, or {@code -1} for unknown values
     */
    static int providerStateCode(ProviderState state) {
        if (state == null) return -1;
        return switch (state) {
            case NOT_READY -> 0;
            case READY -> 1;
            case DEGRADED -> 2;
            case ERROR -> 3;
            case STALE -> 4;
            case SHUTDOWN -> 5;
        };
    }
}
