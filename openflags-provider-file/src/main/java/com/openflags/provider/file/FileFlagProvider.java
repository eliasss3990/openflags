package com.openflags.provider.file;

import com.openflags.core.event.ChangeType;
import com.openflags.core.event.FlagChangeEvent;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.model.Flag;
import com.openflags.core.parser.FlagFileParser;
import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderDiagnostics;
import com.openflags.core.provider.ProviderState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link FlagProvider} that reads flag definitions from a local YAML or JSON file.
 * <p>
 * Supports automatic hot reload when the file changes on disk (via {@link FileWatcher}).
 * Thread-safe: flag reads use an atomic reference swap; listeners are managed with
 * a {@link CopyOnWriteArrayList}.
 * </p>
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>{@link #init()} is idempotent: calling it on an already-initialized provider is a no-op.</li>
 *   <li>{@link #shutdown()} is idempotent: calling it on an already-shut-down provider is a no-op.</li>
 *   <li>Calling evaluation methods after shutdown throws {@link IllegalStateException}.</li>
 * </ul>
 *
 * <h2>Reload behavior</h2>
 * <p>
 * When the file changes, the provider re-parses it and atomically replaces the flag map.
 * If parsing fails (e.g., mid-write), the previous flags are retained and the state is set
 * to {@link ProviderState#ERROR} with a warning log. On successful reload, state returns to READY.
 * </p>
 *
 * <p>Use {@link FileFlagProviderBuilder} (via {@link #builder()}) to create instances.</p>
 */
public final class FileFlagProvider implements FlagProvider, ProviderDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(FileFlagProvider.class);

    private final Path filePath;
    private final boolean watchEnabled;
    private final FlagFileParser parser;
    private final List<FlagChangeListener> listeners = new CopyOnWriteArrayList<>();

    private final AtomicReference<Map<String, Flag>> flags = new AtomicReference<>(Collections.emptyMap());
    private final AtomicReference<ProviderState> state = new AtomicReference<>(ProviderState.NOT_READY);
    private volatile Instant lastReloadAt;

    // volatile: read by init() guard outside synchronized blocks when a subclass or test inspects it
    private volatile boolean initialized = false;
    // volatile: read by requireNotShutdown() outside synchronized blocks
    private volatile boolean shutdown = false;
    // volatile: written inside synchronized init(), read inside synchronized shutdown()
    private volatile FileWatcher watcher;

    FileFlagProvider(Path filePath, boolean watchEnabled) {
        this.filePath = Objects.requireNonNull(filePath, "filePath must not be null");
        this.watchEnabled = watchEnabled;
        this.parser = new FlagFileParser();
    }

    /**
     * Creates a builder for {@code FileFlagProvider}.
     *
     * @return a new builder
     */
    public static FileFlagProviderBuilder builder() {
        return new FileFlagProviderBuilder();
    }

    @Override
    public synchronized void init() {
        if (initialized) return;
        requireNotShutdown();

        Map<String, Flag> loaded = parseFile();
        flags.set(loaded);
        lastReloadAt = Instant.now();
        state.set(ProviderState.READY);
        initialized = true;

        if (watchEnabled) {
            watcher = new FileWatcher(filePath, this::reload);
            watcher.start();
            log.info("FileFlagProvider watching '{}' for changes", filePath);
        }
        log.info("FileFlagProvider initialized with {} flags from '{}'", loaded.size(), filePath);
    }

    @Override
    public Optional<Flag> getFlag(String key) {
        Objects.requireNonNull(key, "key must not be null");
        requireNotShutdown();
        return Optional.ofNullable(flags.get().get(key));
    }

    @Override
    public Map<String, Flag> getAllFlags() {
        requireNotShutdown();
        return flags.get();
    }

    @Override
    public ProviderState getState() {
        return state.get();
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

    @Override
    public synchronized void shutdown() {
        if (shutdown) return;
        shutdown = true;
        if (watcher != null) {
            watcher.stop();
        }
        state.set(ProviderState.STALE);
        log.info("FileFlagProvider shut down");
    }

    private void reload() {
        if (shutdown) {
            log.debug("Ignoring reload() after shutdown for '{}'", filePath);
            return;
        }
        Map<String, Flag> oldFlags = flags.get();
        Map<String, Flag> newFlags;
        try {
            newFlags = parseFile();
        } catch (ProviderException e) {
            synchronized (this) {
                if (!shutdown) {
                    state.set(ProviderState.ERROR);
                    log.warn("Failed to reload flags from '{}'", filePath, e);
                }
            }
            return;
        }
        boolean shouldEmit;
        synchronized (this) {
            if (shutdown) {
                return;
            }
            flags.set(newFlags);
            lastReloadAt = Instant.now();
            state.set(ProviderState.READY);
            shouldEmit = true;
        }
        log.debug("Reloaded flags from '{}': {} flags", filePath, newFlags.size());
        if (shouldEmit) {
            emitChangeEvents(oldFlags, newFlags);
        }
    }

    private Map<String, Flag> parseFile() {
        try {
            return parser.parse(filePath);
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException("Unexpected error parsing flag file: " + filePath, e);
        }
    }

    private void emitChangeEvents(Map<String, Flag> oldFlags, Map<String, Flag> newFlags) {
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(oldFlags.keySet());
        allKeys.addAll(newFlags.keySet());

        for (String key : allKeys) {
            Flag oldFlag = oldFlags.get(key);
            Flag newFlag = newFlags.get(key);

            FlagChangeEvent event = null;
            if (oldFlag == null) {
                event = new FlagChangeEvent(key, newFlag.type(),
                        Optional.empty(), Optional.of(newFlag.value()), ChangeType.CREATED);
            } else if (newFlag == null) {
                event = new FlagChangeEvent(key, oldFlag.type(),
                        Optional.of(oldFlag.value()), Optional.empty(), ChangeType.DELETED);
            } else if (!oldFlag.equals(newFlag)) {
                event = new FlagChangeEvent(key, newFlag.type(),
                        Optional.of(oldFlag.value()), Optional.of(newFlag.value()), ChangeType.UPDATED);
            }

            if (event != null) {
                FlagChangeEvent finalEvent = event;
                listeners.forEach(l -> {
                    try {
                        l.onFlagChange(finalEvent);
                    } catch (Exception e) {
                        log.warn("FlagChangeListener threw an exception", e);
                    }
                });
            }
        }
    }

    @Override
    public String providerType() {
        return "file";
    }

    @Override
    public Map<String, Object> diagnostics() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("file.path", filePath.toString());
        data.put("file.format", detectFormat(filePath));
        Instant updatedAt = lastReloadAt;
        data.put("file.last_reload",
                updatedAt == null ? "" : updatedAt.toString());
        FileWatcher current = watcher;
        data.put("file.watcher_alive",
                watchEnabled && current != null && current.isAlive());
        data.put("file.flag_count", flags.get().size());
        return Collections.unmodifiableMap(data);
    }

    @Override
    public Instant lastUpdate() {
        return lastReloadAt;
    }

    @Override
    public int flagCount() {
        return flags.get().size();
    }

    private static String detectFormat(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".yaml") || name.endsWith(".yml")) return "yaml";
        if (name.endsWith(".json")) return "json";
        return "unknown";
    }

    private void requireNotShutdown() {
        if (shutdown) {
            throw new IllegalStateException("FileFlagProvider has been shut down");
        }
    }
}
