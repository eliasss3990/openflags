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
    /**
     * Provider data is known to be outdated (e.g., remote connection lost).
     *
     * <p><b>Deprecated.</b> No built-in provider emits this state in 1.x.
     * {@code STALE} is scheduled for removal in 2.0 (see ADR-6).
     * Callers <em>must not</em> add new {@code switch} branches or production
     * logic that depends on this value; existing passive consumers (e.g.,
     * health indicators that map it to {@code OUT_OF_SERVICE}) are exempt
     * until the 2.0 removal.
     *
     * @deprecated No producer exists in 1.x; will be removed in 2.0. See ADR-6.
     */
    @Deprecated(forRemoval = true, since = "1.1.0-SNAPSHOT")
    STALE,
    /** Provider has been shut down and is no longer serving flags. */
    SHUTDOWN
}
