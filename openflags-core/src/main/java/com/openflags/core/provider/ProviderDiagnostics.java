package com.openflags.core.provider;

import java.time.Instant;
import java.util.Map;

/**
 * Optional diagnostics surface that {@link FlagProvider} implementations
 * may expose. Consumers (health indicators, monitoring dashboards) can
 * test a provider with {@code instanceof ProviderDiagnostics} and pull
 * runtime details without relying on a specific implementation type.
 *
 * <p>
 * All methods must be safe to call from any thread at any point of the
 * provider lifecycle, including before {@link FlagProvider#init()} and
 * after {@link FlagProvider#shutdown()}. Implementations must not throw.
 * </p>
 */
public interface ProviderDiagnostics {

    /**
     * Stable, lower-case, machine-readable type identifier (for example
     * {@code "file"}, {@code "remote"}, {@code "hybrid"}, {@code "memory"}).
     * Never null or blank.
     *
     * @return the provider type identifier
     */
    String providerType();

    /**
     * Implementation-specific diagnostic key/value pairs. Keys must be
     * dotted, lower-case, namespaced by {@link #providerType()} (for
     * example {@code "file.path"}, {@code "remote.last_status"}).
     * Values must be primitive or {@link String} so they serialize cleanly
     * to JSON.
     *
     * @return an unmodifiable map of diagnostic data; never null,
     *         possibly empty
     */
    Map<String, Object> diagnostics();

    /**
     * Timestamp of the most recent successful flag data refresh, or
     * {@code null} if the provider has never loaded data successfully.
     *
     * @return the last update instant, or {@code null}
     */
    Instant lastUpdate();

    /**
     * Number of flags currently held by the provider. Zero before
     * {@link FlagProvider#init()} or after {@link FlagProvider#shutdown()}.
     *
     * @return the flag count; never negative
     */
    int flagCount();
}
