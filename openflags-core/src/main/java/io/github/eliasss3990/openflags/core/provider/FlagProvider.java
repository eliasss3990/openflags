package io.github.eliasss3990.openflags.core.provider;

import io.github.eliasss3990.openflags.core.event.FlagChangeListener;
import io.github.eliasss3990.openflags.core.exception.ProviderException;
import io.github.eliasss3990.openflags.core.model.Flag;

import java.util.Map;
import java.util.Optional;

/**
 * Central abstraction for flag data sources.
 * <p>
 * Every provider implementation (file, remote, hybrid, in-memory) must implement
 * this interface. Providers must be thread-safe.
 * </p>
 *
 * <h2>Lifecycle</h2>
 * <p>
 * Implementations follow three formal phases:
 * </p>
 * <ol>
 *   <li><strong>created</strong> — the provider has been constructed but
 *       {@link #init()} has not yet been invoked. Implementations
 *       <strong>must not</strong> emit {@link io.github.eliasss3990.openflags.core.event.FlagChangeEvent}
 *       instances during this phase. Setters that mutate flag state are
 *       permitted (their effects become visible after {@code init()} returns),
 *       but no listener notification is allowed.</li>
 *   <li><strong>initialized</strong> — {@link #init()} has completed
 *       successfully. The provider honors evaluation calls, may emit change
 *       events, and reports a meaningful {@link ProviderState}.</li>
 *   <li><strong>shutdown</strong> — {@link #shutdown()} has been invoked.
 *       Both {@link #addChangeListener(io.github.eliasss3990.openflags.core.event.FlagChangeListener)}
 *       and {@link #removeChangeListener(io.github.eliasss3990.openflags.core.event.FlagChangeListener)}
 *       behave as no-ops; evaluation calls follow each provider's documented
 *       post-shutdown contract but must not throw {@code NullPointerException}
 *       on internal state.</li>
 * </ol>
 *
 * <p>See ADR-2 for the rationale.</p>
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
     * @throws NullPointerException if key is null
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
     * must not block on listener invocations. After {@link #shutdown()} this
     * method is a no-op (mirrors {@link #removeChangeListener}); listeners
     * registered before {@code init()} only observe events from the
     * {@code initialized} phase onward.
     * </p>
     *
     * @param listener the listener to register; must not be null
     */
    void addChangeListener(FlagChangeListener listener);

    /**
     * Removes a previously registered listener.
     * <p>
     * If the listener was not registered, this method is a no-op. After
     * {@link #shutdown()} it is also a no-op so cleanup paths can run
     * unconditionally.
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
