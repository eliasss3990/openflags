package com.openflags.core.provider;

import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.model.Flag;

import java.util.Map;
import java.util.Optional;

/**
 * Central abstraction for flag data sources.
 * <p>
 * Every provider implementation (file, remote, hybrid, in-memory) must implement
 * this interface. Providers must be thread-safe.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Create the provider (via builder or constructor).</li>
 *   <li>Call {@link #init()} to load initial flag data. This must be done before evaluation.</li>
 *   <li>Use {@link #getFlag(String)} and {@link #getAllFlags()} for flag evaluation.</li>
 *   <li>Call {@link #shutdown()} when the provider is no longer needed.</li>
 * </ol>
 */
public interface FlagProvider {

    /**
     * Initializes the provider, loading initial flag data.
     * <p>
     * This method blocks until the provider is ready to serve flags or throws on failure.
     * Implementations must be <strong>idempotent</strong>: calling {@code init()} on an
     * already-initialized provider is a no-op.
     * </p>
     *
     * @throws ProviderException if initialization fails (e.g., file not found, parse error)
     */
    void init();

    /**
     * Returns the flag definition for the given key.
     *
     * @param key the flag key; must not be null
     * @return the flag if found, empty otherwise
     */
    Optional<Flag> getFlag(String key);

    /**
     * Returns all flag definitions currently known by this provider.
     *
     * @return an unmodifiable map of key to {@link Flag}; never null
     */
    Map<String, Flag> getAllFlags();

    /**
     * Returns the current lifecycle state of this provider.
     *
     * @return the provider state; never null
     */
    ProviderState getState();

    /**
     * Registers a listener to receive flag change events.
     * <p>
     * Listeners are notified synchronously when flags change. Implementations
     * must not block on listener invocations.
     * </p>
     *
     * @param listener the listener to register; must not be null
     */
    void addChangeListener(FlagChangeListener listener);

    /**
     * Removes a previously registered listener.
     * <p>
     * If the listener was not registered, this method is a no-op.
     * </p>
     *
     * @param listener the listener to remove
     */
    void removeChangeListener(FlagChangeListener listener);

    /**
     * Shuts down the provider, releasing all resources (threads, file handles, connections).
     * <p>
     * Implementations must be <strong>idempotent</strong>: calling {@code shutdown()} on an
     * already-shut-down provider is a no-op.
     * After shutdown, calling any method other than {@code shutdown()} or {@link #getState()}
     * throws {@link IllegalStateException}.
     * </p>
     */
    void shutdown();
}
