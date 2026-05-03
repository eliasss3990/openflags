package com.openflags.provider.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.openflags.core.event.ChangeType;
import com.openflags.core.event.FlagChangeEvent;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.model.Flag;
import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderDiagnostics;
import com.openflags.core.provider.ProviderState;
import com.openflags.core.parser.FlagFileParser;
import com.openflags.core.util.Urls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link FlagProvider} implementation that fetches flag definitions from a
 * remote
 * HTTP endpoint and caches them locally with a configurable TTL and polling
 * interval.
 *
 * <h2>Resilience</h2>
 * <p>
 * The provider follows a stale-while-error policy: if a poll fails, the
 * previous cache
 * keeps serving until a successful fetch happens. The provider transitions
 * through:
 * </p>
 * <ul>
 * <li>{@link ProviderState#NOT_READY} → {@link ProviderState#READY} on a
 * successful {@code init()}.</li>
 * <li>{@link ProviderState#READY} → {@link ProviderState#DEGRADED} on a poll
 * failure that has not exceeded the cache TTL.</li>
 * <li>{@link ProviderState#DEGRADED} → {@link ProviderState#ERROR} when
 * {@code now - lastSuccessfulFetch > cacheTtl}.</li>
 * <li>Any state → {@link ProviderState#SHUTDOWN} after
 * {@link #shutdown()}.</li>
 * </ul>
 * <p>
 * In all states except {@code SHUTDOWN} and {@code NOT_READY},
 * {@link #getFlag(String)} returns
 * the most recently successfully fetched data.
 * </p>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This class is thread-safe. The underlying cache is swapped atomically on each
 * successful poll via a single {@code volatile} {@link CacheSnapshot}
 * reference;
 * readers always see a consistent snapshot. {@link #init()} and
 * {@link #shutdown()}
 * are {@code synchronized} to prevent concurrent scheduler creation or
 * double-shutdown.
 * </p>
 */
public final class RemoteFlagProvider implements FlagProvider, ProviderDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(RemoteFlagProvider.class);

    /**
     * Immutable snapshot of the cache state. Written atomically via a single
     * volatile field.
     */
    private record CacheSnapshot(
            Map<String, Flag> flags,
            Instant fetchedAt,
            ProviderState state) {
    }

    private static final CacheSnapshot INITIAL_SNAPSHOT = new CacheSnapshot(Map.of(), Instant.EPOCH,
            ProviderState.NOT_READY);

    private final RemoteProviderConfig config;
    private final RemoteHttpClient httpClient;
    private final FlagFileParser parser;
    private final ObjectMapper objectMapper;
    private final CopyOnWriteArrayList<FlagChangeListener> listeners;

    private volatile CacheSnapshot snapshot = INITIAL_SNAPSHOT;
    private volatile RemotePollListener pollListener;

    private final CircuitBreakerState circuitBreaker;

    /**
     * Nanos timestamp ({@link System#nanoTime()}) when the last poll task was
     * scheduled.
     */
    private final AtomicLong lastScheduleNanos = new AtomicLong(0L);
    /** Delay (millis) of the most recently scheduled poll. */
    private final AtomicLong lastScheduleDelayMillis = new AtomicLong(0L);

    private ScheduledExecutorService scheduler;

    /**
     * Use {@link RemoteFlagProviderBuilder} to construct instances.
     */
    RemoteFlagProvider(RemoteProviderConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.httpClient = new RemoteHttpClient(config);
        this.parser = new FlagFileParser();
        this.objectMapper = JsonMapper.builder().build();
        this.listeners = new CopyOnWriteArrayList<>();
        this.circuitBreaker = new CircuitBreakerState(
                config.failureThreshold(), config.pollInterval(), config.maxBackoff());
        if (config.maxBackoff().compareTo(config.pollInterval()) == 0) {
            log.warn("maxBackoff equals pollInterval ({}); circuit breaker backoff "
                    + "will have no effect — consider raising maxBackoff", config.pollInterval());
        }
    }

    /**
     * Registers a {@link RemotePollListener} that will be called once per
     * successful
     * poll cycle, after all individual
     * {@link com.openflags.core.event.FlagChangeEvent}s
     * have been emitted. Replaces any previously registered listener.
     *
     * @param listener the listener to register; may be null to clear
     */
    public void setPollListener(RemotePollListener listener) {
        this.pollListener = listener;
    }

    /**
     * Performs the initial fetch synchronously and starts the polling thread.
     * Idempotent: subsequent calls return without effect once the snapshot
     * state is anything other than {@link ProviderState#NOT_READY}.
     *
     * @throws ProviderException if the initial fetch fails (network, parse, or HTTP
     *                           error)
     */
    @Override
    public synchronized void init() {
        if (snapshot.state() != ProviderState.NOT_READY) {
            return; // already initialized (or shut down)
        }
        try {
            pollOnce();
            if (snapshot.state() != ProviderState.READY) {
                // pollOnce transitioned to DEGRADED or ERROR due to non-2xx; reset and throw
                snapshot = INITIAL_SNAPSHOT;
                throw new ProviderException("Initial fetch did not produce a READY state");
            }
            startScheduler();
        } catch (ProviderException e) {
            snapshot = INITIAL_SNAPSHOT;
            throw e;
        } catch (Exception e) {
            snapshot = INITIAL_SNAPSHOT;
            throw new ProviderException(
                    "Failed to initialize remote provider for " + config.baseUrl(), e);
        }
    }

    @Override
    public Optional<Flag> getFlag(String key) {
        Objects.requireNonNull(key, "key must not be null");
        checkCanServe();
        return Optional.ofNullable(snapshot.flags().get(key));
    }

    @Override
    public Map<String, Flag> getAllFlags() {
        checkCanServe();
        return snapshot.flags();
    }

    @Override
    public ProviderState getState() {
        return snapshot.state();
    }

    @Override
    public void addChangeListener(FlagChangeListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    @Override
    public void removeChangeListener(FlagChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Stops the polling thread and releases all resources. Idempotent.
     * <p>
     * The HTTP client is closed first to cancel in-flight requests, then the
     * scheduler is drained up to {@link RemoteProviderConfig#shutdownTimeout()}.
     * </p>
     */
    @Override
    public synchronized void shutdown() {
        if (snapshot.state() == ProviderState.SHUTDOWN) {
            return;
        }
        snapshot = new CacheSnapshot(snapshot.flags(), snapshot.fetchedAt(), ProviderState.SHUTDOWN);
        // Close the HTTP client first so that any in-flight request unblocks quickly.
        httpClient.close();
        if (scheduler != null) {
            scheduler.shutdown();
            long timeoutMs = config.shutdownTimeout().toMillis();
            try {
                if (!scheduler.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        listeners.clear();
        log.info("RemoteFlagProvider shut down for {}", config.baseUrl());
    }

    private void checkCanServe() {
        ProviderState current = snapshot.state();
        if (current == ProviderState.NOT_READY) {
            throw new IllegalStateException("provider not initialized");
        }
        if (current == ProviderState.SHUTDOWN) {
            throw new IllegalStateException("provider shut down");
        }
    }

    private void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "openflags-remote-poller");
            t.setDaemon(true);
            return t;
        });
        scheduleNextPoll(config.pollInterval());
    }

    private void scheduleNextPoll(Duration delay) {
        if (snapshot.state() == ProviderState.SHUTDOWN || scheduler == null || scheduler.isShutdown()) {
            return;
        }
        long millis = delay.toMillis();
        lastScheduleNanos.set(System.nanoTime());
        lastScheduleDelayMillis.set(millis);
        try {
            scheduler.schedule(this::pollAndReschedule, millis, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // scheduler shut down between the guard and schedule(); benign.
        }
    }

    private void pollAndReschedule() {
        long started = System.nanoTime();
        String outcome;
        long durationNanos;
        try {
            outcome = pollOnceWithCircuitBreaker();
            durationNanos = System.nanoTime() - started;
        } catch (Exception e) {
            // Capture duration of the network/parse work before logging or follow-up
            // bookkeeping so the metric reflects the poll itself, not the catch path.
            durationNanos = System.nanoTime() - started;
            log.warn("Unexpected error in poll loop for {}", config.baseUrl(), e);
            circuitBreaker.recordFailure();
            checkStaleness();
            logThrottledFailure();
            outcome = "failure";
        }
        notifyPollOutcome(outcome, durationNanos);
        scheduleNextPoll(circuitBreaker.nextDelay());
    }

    private String pollOnceWithCircuitBreaker() throws Exception {
        String outcome = pollOnce();
        // 200/204 → success; 304 → not_modified (still a healthy contact);
        // anything else (non-2xx without throwing) → failure.
        if ("success".equals(outcome) || "not_modified".equals(outcome)) {
            circuitBreaker.recordSuccess();
        } else {
            circuitBreaker.recordFailure();
            logThrottledFailure();
        }
        return outcome;
    }

    private void logThrottledFailure() {
        int failures = circuitBreaker.failureCount();
        if (failures > 0 && (failures & (failures - 1)) == 0) {
            log.warn("Remote poll failed for {} (count={}); next attempt in ~{}ms",
                    config.baseUrl(), failures, circuitBreaker.nextDelay().toMillis());
        }
    }

    private String pollOnce() throws Exception {
        HttpResponse<String> response = httpClient.fetch();
        int status = response.statusCode();

        if (status == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            Map<String, Flag> newFlags = parser.parseFlags(root, "remote:" + config.baseUrl());
            Map<String, Flag> oldFlags = snapshot.flags();
            snapshot = new CacheSnapshot(Map.copyOf(newFlags), Instant.now(), ProviderState.READY);
            emitDiff(oldFlags, snapshot.flags());
            notifyPollComplete(snapshot.flags());
            log.debug("Poll succeeded for {}: {} flags loaded", config.baseUrl(), snapshot.flags().size());
            return "success";

        } else if (status == 204) {
            Map<String, Flag> oldFlags = snapshot.flags();
            if (!oldFlags.isEmpty()) {
                log.warn("Remote returned 204 — clearing {} flags for {}", oldFlags.size(), config.baseUrl());
            }
            snapshot = new CacheSnapshot(Map.of(), Instant.now(), ProviderState.READY);
            emitDiff(oldFlags, Map.of());
            notifyPollComplete(Map.of());
            log.debug("Poll returned 204 for {}: cache cleared", config.baseUrl());
            return "success";

        } else if (status == 304) {
            log.warn("Poll returned 304 for {} (unexpected; no If-None-Match sent)", config.baseUrl());
            // keep cache as-is
            return "not_modified";

        } else if (status == 401 || status == 403) {
            // throw and let the caller decide:
            // - init() rethrows so the user sees a clear auth-failed error
            // - pollAndReschedule() catches Throwable, logs once and runs checkStaleness()
            throw new ProviderException(
                    "Authentication failed: HTTP " + status + " for " + config.baseUrl()
                            + " — check auth configuration");

        } else {
            log.warn("Poll returned HTTP {} for {}", status, config.baseUrl());
            checkStaleness();
            return "failure";
        }
    }

    void checkStaleness() {
        CacheSnapshot current = snapshot;
        if (current.state() == ProviderState.SHUTDOWN) {
            return;
        }
        // Skip if no successful fetch has happened yet (fetchedAt is EPOCH sentinel).
        // Age would be ~55 years, always exceeding cacheTtl and producing a spurious ERROR.
        if (current.fetchedAt().equals(Instant.EPOCH)) {
            return;
        }
        long ageMillis = Instant.now().toEpochMilli() - current.fetchedAt().toEpochMilli();
        ProviderState next = ageMillis > config.cacheTtl().toMillis()
                ? ProviderState.ERROR
                : ProviderState.DEGRADED;
        if (next == ProviderState.ERROR) {
            log.warn("Cache TTL exceeded for {}; transitioning to ERROR", config.baseUrl());
        }
        snapshot = new CacheSnapshot(current.flags(), current.fetchedAt(), next);
    }

    private void emitDiff(Map<String, Flag> oldFlags, Map<String, Flag> newFlags) {
        for (Map.Entry<String, Flag> entry : newFlags.entrySet()) {
            String key = entry.getKey();
            Flag newFlag = entry.getValue();
            if (!oldFlags.containsKey(key)) {
                notifyListeners(new FlagChangeEvent(key, newFlag.type(),
                        Optional.empty(), Optional.of(newFlag.value()), ChangeType.CREATED));
            } else {
                Flag oldFlag = oldFlags.get(key);
                if (!oldFlag.equals(newFlag)) {
                    ChangeType ct = ChangeType.resolveUpdate(newFlag.type(), oldFlag.value(), newFlag.value());
                    notifyListeners(new FlagChangeEvent(key, newFlag.type(),
                            Optional.of(oldFlag.value()), Optional.of(newFlag.value()), ct));
                }
            }
        }
        for (Map.Entry<String, Flag> entry : oldFlags.entrySet()) {
            String key = entry.getKey();
            if (!newFlags.containsKey(key)) {
                Flag oldFlag = entry.getValue();
                notifyListeners(new FlagChangeEvent(key, oldFlag.type(),
                        Optional.of(oldFlag.value()), Optional.empty(), ChangeType.DELETED));
            }
        }
    }

    private void notifyPollComplete(Map<String, Flag> flagSnapshot) {
        RemotePollListener pl = pollListener;
        if (pl != null) {
            try {
                pl.onPollComplete(flagSnapshot);
            } catch (Exception e) {
                log.warn("RemotePollListener threw an exception", e);
            }
        }
    }

    private void notifyPollOutcome(String outcome, long durationNanos) {
        RemotePollListener pl = pollListener;
        if (pl != null) {
            try {
                pl.onPollOutcome(outcome, durationNanos);
            } catch (Exception e) {
                log.warn("RemotePollListener.onPollOutcome threw an exception", e);
            }
        }
    }

    @Override
    public String providerType() {
        return "remote";
    }

    @Override
    public Map<String, Object> diagnostics() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("remote.base_url", Urls.redact(config.baseUrl()));
        data.put("remote.poll_interval_ms", config.pollInterval().toMillis());
        data.put("remote.cache_ttl_ms", config.cacheTtl().toMillis());
        data.put("remote.state", snapshot.state().name());
        Instant fetched = snapshot.fetchedAt();
        data.put("remote.last_fetch",
                fetched.equals(Instant.EPOCH) ? "" : fetched.toString());
        data.put("remote.flag_count", snapshot.flags().size());
        data.put("remote.consecutive_failures", circuitBreaker.failureCount());
        data.put("remote.circuit_open", circuitBreaker.isOpen());
        data.put("remote.next_poll_in_ms", computeNextPollInMs());
        return Collections.unmodifiableMap(data);
    }

    private long computeNextPollInMs() {
        // Best-effort: the two atomics are read independently, so a concurrent
        // re-schedule between reads can mix a fresh timestamp with a stale delay.
        // Acceptable for diagnostics; consumers should treat the value as approximate.
        long scheduledNanos = lastScheduleNanos.get();
        if (scheduledNanos == 0L) {
            return 0L;
        }
        long elapsedMillis = (System.nanoTime() - scheduledNanos) / 1_000_000L;
        long remaining = lastScheduleDelayMillis.get() - elapsedMillis;
        return Math.max(remaining, 0L);
    }

    @Override
    public Instant lastUpdate() {
        Instant fetched = snapshot.fetchedAt();
        return fetched.equals(Instant.EPOCH) ? null : fetched;
    }

    @Override
    public int flagCount() {
        return snapshot.state() == ProviderState.SHUTDOWN ? 0 : snapshot.flags().size();
    }

    private void notifyListeners(FlagChangeEvent event) {
        for (FlagChangeListener listener : listeners) {
            try {
                listener.onFlagChange(event);
            } catch (Exception e) {
                log.warn("FlagChangeListener threw an exception", e);
            }
        }
    }
}
