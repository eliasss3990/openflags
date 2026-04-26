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
import com.openflags.provider.file.FlagFileParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
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
 * <p>This class is thread-safe. The underlying flags map is swapped atomically on each
 * successful poll; readers always see a consistent snapshot.</p>
 */
public final class RemoteFlagProvider implements FlagProvider {

    private static final Logger log = LoggerFactory.getLogger(RemoteFlagProvider.class);

    private final RemoteProviderConfig config;
    private final RemoteHttpClient httpClient;
    private final FlagFileParser parser;
    private final ObjectMapper objectMapper;
    private final CopyOnWriteArrayList<FlagChangeListener> listeners;

    private volatile Map<String, Flag> flagsRef = Map.of();
    private volatile Instant lastSuccessfulFetch = Instant.EPOCH;
    private volatile ProviderState state = ProviderState.NOT_READY;

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
     * Performs the initial fetch synchronously and starts the polling thread.
     *
     * @throws ProviderException if the initial fetch fails (network, parse, or HTTP error)
     */
    @Override
    public void init() {
        if (state == ProviderState.READY) {
            return; // idempotent
        }
        try {
            pollOnce();
            if (state != ProviderState.READY) {
                // pollOnce transitioned to DEGRADED or ERROR due to non-2xx; reset and throw
                state = ProviderState.NOT_READY;
                throw new ProviderException("Initial fetch did not produce a READY state");
            }
            startScheduler();
        } catch (ProviderException e) {
            state = ProviderState.NOT_READY;
            throw e;
        } catch (Exception e) {
            state = ProviderState.NOT_READY;
            throw new ProviderException(
                    "Failed to initialize remote provider for " + config.baseUrl(), e);
        }
    }

    @Override
    public Optional<Flag> getFlag(String key) {
        Objects.requireNonNull(key, "key must not be null");
        checkCanServe();
        return Optional.ofNullable(flagsRef.get(key));
    }

    @Override
    public Map<String, Flag> getAllFlags() {
        checkCanServe();
        return flagsRef;
    }

    @Override
    public ProviderState getState() {
        return state;
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
    public void shutdown() {
        if (state == ProviderState.SHUTDOWN) {
            return;
        }
        state = ProviderState.SHUTDOWN;
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
        ProviderState current = state;
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
            Map<String, Flag> oldFlags = flagsRef;
            flagsRef = Map.copyOf(newFlags);
            lastSuccessfulFetch = Instant.now();
            state = ProviderState.READY;
            emitDiff(oldFlags, flagsRef);
            log.debug("Poll succeeded for {}: {} flags loaded", config.baseUrl(), flagsRef.size());

        } else if (status == 204) {
            Map<String, Flag> oldFlags = flagsRef;
            flagsRef = Map.of();
            lastSuccessfulFetch = Instant.now();
            state = ProviderState.READY;
            emitDiff(oldFlags, Map.of());
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
        if (state == ProviderState.SHUTDOWN) {
            return;
        }
        Instant now = Instant.now();
        long ageMillis = now.toEpochMilli() - lastSuccessfulFetch.toEpochMilli();
        if (ageMillis > config.cacheTtl().toMillis()) {
            state = ProviderState.ERROR;
            log.warn("Cache TTL exceeded for {}; transitioning to ERROR", config.baseUrl());
        } else {
            state = ProviderState.DEGRADED;
        }
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
