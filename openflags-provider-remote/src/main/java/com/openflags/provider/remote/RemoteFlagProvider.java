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
import com.openflags.core.provider.ProviderState;
import com.openflags.core.parser.FlagFileParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A {@link FlagProvider} implementation that fetches flag definitions from a remote
 * HTTP endpoint and caches them locally with a configurable TTL and polling interval.
 *
 * <h2>Resilience</h2>
 * <p>
 * The provider follows a stale-while-error policy: if a poll fails, the previous cache
 * keeps serving until a successful fetch happens. The provider transitions through:
 * </p>
 * <ul>
 *   <li>{@link ProviderState#NOT_READY} → {@link ProviderState#READY} on a successful {@code init()}.</li>
 *   <li>{@link ProviderState#READY} → {@link ProviderState#DEGRADED} on a poll failure that has not exceeded the cache TTL.</li>
 *   <li>{@link ProviderState#DEGRADED} → {@link ProviderState#ERROR} when {@code now - lastSuccessfulFetch > cacheTtl}.</li>
 *   <li>Any state → {@link ProviderState#SHUTDOWN} after {@link #shutdown()}.</li>
 * </ul>
 * <p>
 * In all states except {@code SHUTDOWN} and {@code NOT_READY}, {@link #getFlag(String)} returns
 * the most recently successfully fetched data.
 * </p>
 *
 * <h2>Thread-safety</h2>
 * <p>This class is thread-safe. The underlying cache is swapped atomically on each
 * successful poll via a single {@code volatile} {@link CacheSnapshot} reference;
 * readers always see a consistent snapshot. {@link #init()} and {@link #shutdown()}
 * are {@code synchronized} to prevent concurrent scheduler creation or double-shutdown.</p>
 */
public final class RemoteFlagProvider implements FlagProvider {

    private static final Logger log = LoggerFactory.getLogger(RemoteFlagProvider.class);

    /**
     * Immutable snapshot of the cache state. Written atomically via a single volatile field.
     */
    private record CacheSnapshot(
            Map<String, Flag> flags,
            Instant fetchedAt,
            ProviderState state
    ) {}

    private static final CacheSnapshot INITIAL_SNAPSHOT =
            new CacheSnapshot(Map.of(), Instant.EPOCH, ProviderState.NOT_READY);

    private final RemoteProviderConfig config;
    private final RemoteHttpClient httpClient;
    private final FlagFileParser parser;
    private final ObjectMapper objectMapper;
    private final CopyOnWriteArrayList<FlagChangeListener> listeners;

    private volatile CacheSnapshot snapshot = INITIAL_SNAPSHOT;
    private volatile RemotePollListener pollListener;

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
    }

    /**
     * Registers a {@link RemotePollListener} that will be called once per successful
     * poll cycle, after all individual {@link com.openflags.core.event.FlagChangeEvent}s
     * have been emitted. Replaces any previously registered listener.
     *
     * @param listener the listener to register; may be null to clear
     */
    public void setPollListener(RemotePollListener listener) {
        this.pollListener = listener;
    }

    /**
     * Performs the initial fetch synchronously and starts the polling thread.
     * Idempotent: safe to call multiple times or from multiple threads; only the
     * first call when {@code state == NOT_READY} takes effect.
     *
     * @throws ProviderException if the initial fetch fails (network, parse, or HTTP error)
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
     */
    @Override
    public synchronized void shutdown() {
        if (snapshot.state() == ProviderState.SHUTDOWN) {
            return;
        }
        snapshot = new CacheSnapshot(snapshot.flags(), snapshot.fetchedAt(), ProviderState.SHUTDOWN);
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        httpClient.close();
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
        scheduler.scheduleAtFixedRate(
                this::pollSafe,
                config.pollInterval().toMillis(),
                config.pollInterval().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private void pollSafe() {
        try {
            pollOnce();
        } catch (Throwable t) {
            log.warn("Unexpected error in poll loop for {}", config.baseUrl(), t);
            checkStaleness();
        }
    }

    private void pollOnce() throws Exception {
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

        } else if (status == 204) {
            Map<String, Flag> oldFlags = snapshot.flags();
            if (!oldFlags.isEmpty()) {
                log.warn("Remote returned 204 — clearing {} flags for {}", oldFlags.size(), config.baseUrl());
            }
            snapshot = new CacheSnapshot(Map.of(), Instant.now(), ProviderState.READY);
            emitDiff(oldFlags, Map.of());
            notifyPollComplete(Map.of());
            log.debug("Poll returned 204 for {}: cache cleared", config.baseUrl());

        } else if (status == 304) {
            log.warn("Poll returned 304 for {} (unexpected; no If-None-Match sent)", config.baseUrl());
            // keep cache as-is

        } else if (status == 401) {
            log.error("Poll returned 401 Unauthorized for {} — check auth configuration", config.baseUrl());
            checkStaleness();

        } else {
            log.warn("Poll returned HTTP {} for {}", status, config.baseUrl());
            checkStaleness();
        }
    }

    private void checkStaleness() {
        CacheSnapshot current = snapshot;
        if (current.state() == ProviderState.SHUTDOWN) {
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
                    notifyListeners(new FlagChangeEvent(key, newFlag.type(),
                            Optional.of(oldFlag.value()), Optional.of(newFlag.value()), ChangeType.UPDATED));
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
