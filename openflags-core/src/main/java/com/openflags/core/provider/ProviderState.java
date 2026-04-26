package com.openflags.core.provider;

/**
 * Represents the lifecycle state of a {@link FlagProvider}.
 */
public enum ProviderState {
    /** Provider has been created but {@code init()} has not been called yet. */
    NOT_READY,
    /** Provider is initialized and actively serving flags. */
    READY,
    /**
     * Provider is serving cached data while experiencing transient failures.
     * The cache TTL has not been exceeded yet.
     */
    DEGRADED,
    /**
     * Provider encountered an error. It may be serving stale data from before
     * the error occurred, or returning default values if no data was ever loaded.
     */
    ERROR,
    /** Provider data is known to be outdated (e.g., remote connection lost). */
    STALE,
    /** Provider has been shut down and is no longer serving flags. */
    SHUTDOWN
}
