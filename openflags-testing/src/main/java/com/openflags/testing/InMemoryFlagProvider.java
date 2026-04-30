package com.openflags.testing;

import com.openflags.core.event.ChangeType;
import com.openflags.core.event.FlagChangeEvent;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link FlagProvider} for testing purposes.
 * <p>
 * Allows programmatic setup of flags without files or external systems.
 * Thread-safe.
 * </p>
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>{@link #init()} is idempotent.</li>
 *   <li>{@link #shutdown()} is idempotent.</li>
 * </ul>
 *
 * <h2>Concurrency model</h2>
 * <ul>
 *   <li>{@code state} — {@code volatile}: written under {@code synchronized} lifecycle methods,
 *       read without lock from {@link #getState()}.</li>
 *   <li>{@code shutdown} — {@code volatile}: written under {@code synchronized} lifecycle methods,
 *       read without lock from {@link #requireNotShutdown()} inside read paths.</li>
 *   <li>{@code flags} — {@link java.util.concurrent.ConcurrentHashMap}: individual put/get/remove
 *       are atomic; compound operations (e.g. {@link #setDisabled}) use {@code computeIfPresent}
 *       for atomicity.</li>
 *   <li>{@code listeners} — {@link java.util.concurrent.CopyOnWriteArrayList}: iteration is
 *       safe without locks; {@link #emit} runs <em>outside</em> any lock, so a listener may
 *       observe events slightly after the flag map has already been updated.</li>
 * </ul>
 *
 * <pre>
 * OpenFlagsClient client = OpenFlagsClient.builder()
 *     .provider(new InMemoryFlagProvider()
 *         .setBoolean("dark-mode", true)
 *         .setString("theme", "dark"))
 *     .build();
 * </pre>
 */
public final class InMemoryFlagProvider implements FlagProvider {

    private static final Logger log = LoggerFactory.getLogger(InMemoryFlagProvider.class);

    private final Map<String, Flag> flags = new ConcurrentHashMap<>();
    private final List<FlagChangeListener> listeners = new CopyOnWriteArrayList<>();
    private volatile ProviderState state = ProviderState.NOT_READY;
    private volatile boolean shutdown = false;

    @Override
    public synchronized void init() {
        if (shutdown) {
            throw new IllegalStateException("InMemoryFlagProvider has been shut down");
        }
        if (state == ProviderState.READY) return;
        state = ProviderState.READY;
    }

    @Override
    public Optional<Flag> getFlag(String key) {
        Objects.requireNonNull(key, "key must not be null");
        requireNotShutdown();
        return Optional.ofNullable(flags.get(key));
    }

    @Override
    public Map<String, Flag> getAllFlags() {
        requireNotShutdown();
        return Collections.unmodifiableMap(new HashMap<>(flags));
    }

    @Override
    public ProviderState getState() {
        return state;
    }

    @Override
    public void addChangeListener(FlagChangeListener listener) {
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
        state = ProviderState.SHUTDOWN;
    }

    /**
     * Sets a boolean flag.
     *
     * @param key   the flag key
     * @param value the flag value
     * @return this provider (for chaining)
     */
    public InMemoryFlagProvider setBoolean(String key, boolean value) {
        return putFlag(new Flag(key, FlagType.BOOLEAN, FlagValue.of(value, FlagType.BOOLEAN), true, null));
    }

    /**
     * Sets a string flag.
     *
     * @param key   the flag key
     * @param value the flag value
     * @return this provider (for chaining)
     */
    public InMemoryFlagProvider setString(String key, String value) {
        return putFlag(new Flag(key, FlagType.STRING, FlagValue.of(value, FlagType.STRING), true, null));
    }

    /**
     * Sets a number flag.
     *
     * @param key   the flag key
     * @param value the flag value
     * @return this provider (for chaining)
     */
    public InMemoryFlagProvider setNumber(String key, double value) {
        return putFlag(new Flag(key, FlagType.NUMBER, FlagValue.of(value, FlagType.NUMBER), true, null));
    }

    /**
     * Sets an object flag.
     *
     * @param key   the flag key
     * @param value the flag value
     * @return this provider (for chaining)
     */
    public InMemoryFlagProvider setObject(String key, Map<String, Object> value) {
        return putFlag(new Flag(key, FlagType.OBJECT, FlagValue.of(value, FlagType.OBJECT), true, null));
    }

    /**
     * Sets a flag as disabled. Evaluation of a disabled flag returns the caller's default value.
     *
     * @param key the flag key (must already be set)
     * @return this provider (for chaining)
     * @throws IllegalArgumentException if the flag does not exist
     */
    public InMemoryFlagProvider setDisabled(String key) {
        requireNotShutdown();
        Flag[] updated = {null};
        flags.computeIfPresent(key, (k, existing) -> {
            Flag disabled = new Flag(k, existing.type(), existing.value(), false, existing.metadata(), existing.rules());
            updated[0] = disabled;
            return disabled;
        });
        if (updated[0] == null) {
            throw new IllegalArgumentException("Cannot disable unknown flag: '" + key + "'");
        }
        emit(new FlagChangeEvent(key, updated[0].type(),
                Optional.of(updated[0].value()), Optional.of(updated[0].value()), ChangeType.UPDATED));
        return this;
    }

    /**
     * Registers a fully constructed {@link Flag}, including any rules.
     * <p>
     * Use this method when the typed convenience setters ({@code setBoolean}, etc.) are not
     * sufficient, for example when the flag carries Phase 2 targeting or split rules.
     * </p>
     *
     * @param flag the flag to register; must not be null
     * @return this provider (for chaining)
     */
    public InMemoryFlagProvider setFlag(Flag flag) {
        Objects.requireNonNull(flag, "flag must not be null");
        return putFlag(flag);
    }

    /**
     * Removes a flag.
     *
     * @param key the flag key to remove
     * @return this provider (for chaining)
     */
    public InMemoryFlagProvider remove(String key) {
        requireNotShutdown();
        Flag removed = flags.remove(key);
        if (removed != null) {
            emit(new FlagChangeEvent(key, removed.type(),
                    Optional.of(removed.value()), Optional.empty(), ChangeType.DELETED));
        }
        return this;
    }

    /**
     * Removes all flags. Emits one {@link ChangeType#DELETED} event per
     * removed flag (no aggregated event), in undefined order.
     *
     * @return this provider (for chaining)
     */
    public InMemoryFlagProvider clear() {
        requireNotShutdown();
        new HashSet<>(flags.keySet()).forEach(this::remove);
        return this;
    }

    private InMemoryFlagProvider putFlag(Flag newFlag) {
        requireNotShutdown();
        Flag oldFlag = flags.put(newFlag.key(), newFlag);
        ChangeType changeType = (oldFlag == null) ? ChangeType.CREATED : ChangeType.UPDATED;
        emit(new FlagChangeEvent(
                newFlag.key(), newFlag.type(),
                Optional.ofNullable(oldFlag == null ? null : oldFlag.value()),
                Optional.of(newFlag.value()),
                changeType));
        return this;
    }

    private void emit(FlagChangeEvent event) {
        listeners.forEach(l -> {
            try {
                l.onFlagChange(event);
            } catch (Exception e) {
                log.warn("FlagChangeListener threw an exception", e);
            }
        });
    }

    private void requireNotShutdown() {
        if (shutdown) {
            throw new IllegalStateException("InMemoryFlagProvider has been shut down");
        }
    }
}
