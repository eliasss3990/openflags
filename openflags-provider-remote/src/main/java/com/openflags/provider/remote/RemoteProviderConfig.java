package com.openflags.provider.remote;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable configuration for a {@link RemoteFlagProvider}.
 *
 * @param baseUrl          the base URL of the backend (e.g.
 *                         {@code https://flags.example.com}); non-null,
 *                         scheme must be {@code http} or {@code https}
 * @param flagsPath        the path appended to {@code baseUrl} for fetching
 *                         flags; default {@code "/flags"}
 * @param authHeaderName   the HTTP header used for authentication (e.g.
 *                         {@code "Authorization"} or
 *                         {@code "X-API-Key"}); may be null to disable auth
 * @param authHeaderValue  the literal header value (e.g.
 *                         {@code "Bearer eyJ..."}); may be null
 *                         if {@code authHeaderName} is null. Never logged.
 * @param connectTimeout   HTTP connect timeout; must be positive
 * @param requestTimeout   HTTP request timeout (per request); must be positive
 * @param pollInterval     interval between polls; must be {@code >= 5s}
 * @param cacheTtl         cache TTL after which the provider transitions to
 *                         {@code ERROR} state
 *                         if no successful fetch happens; must be
 *                         {@code >= pollInterval}
 * @param userAgent        value of the {@code User-Agent} header; non-blank,
 *                         default
 *                         {@code "openflags-java"}
 * @param failureThreshold consecutive poll failures required before the circuit
 *                         breaker opens;
 *                         must be in {@code [1, 100]}; default {@code 5}
 * @param maxBackoff       maximum delay applied between polls when the circuit
 *                         breaker is open;
 *                         must be {@code >= pollInterval}; default {@code 5min}
 */
public record RemoteProviderConfig(
        URI baseUrl,
        String flagsPath,
        String authHeaderName,
        String authHeaderValue,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration pollInterval,
        Duration cacheTtl,
        String userAgent,
        int failureThreshold,
        Duration maxBackoff) {

    /** Sanity ceiling for {@code failureThreshold}. */
    public static final int MAX_FAILURE_THRESHOLD = 100;

    /** Default consecutive-failures threshold before opening the circuit breaker. */
    public static final int DEFAULT_FAILURE_THRESHOLD = 5;

    /** Default maximum backoff applied while the circuit breaker is open. */
    public static final Duration DEFAULT_MAX_BACKOFF = Duration.ofMinutes(5);

    /**
     * Convenience constructor preserving the pre-circuit-breaker signature.
     * Applies {@link #DEFAULT_FAILURE_THRESHOLD} and the larger of
     * {@link #DEFAULT_MAX_BACKOFF} or {@code pollInterval} as defaults.
     */
    public RemoteProviderConfig(
            URI baseUrl,
            String flagsPath,
            String authHeaderName,
            String authHeaderValue,
            Duration connectTimeout,
            Duration requestTimeout,
            Duration pollInterval,
            Duration cacheTtl,
            String userAgent) {
        this(baseUrl, flagsPath, authHeaderName, authHeaderValue,
                connectTimeout, requestTimeout, pollInterval, cacheTtl, userAgent,
                DEFAULT_FAILURE_THRESHOLD,
                pollInterval != null && pollInterval.compareTo(DEFAULT_MAX_BACKOFF) > 0
                        ? pollInterval
                        : DEFAULT_MAX_BACKOFF);
    }

    /** Minimum allowed poll interval to prevent accidental backend overload. */
    public static final Duration MIN_POLL_INTERVAL = Duration.ofSeconds(5);

    /**
     * Compact constructor that validates fields and applies defaults to optional
     * ones.
     *
     * @throws NullPointerException     if any required field is null
     * @throws IllegalArgumentException if any range/scheme constraint is violated
     */
    public RemoteProviderConfig {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        String scheme = baseUrl.getScheme();
        if (scheme == null || !Set.of("http", "https").contains(scheme)) {
            throw new IllegalArgumentException(
                    "baseUrl must use http or https, got " + scheme);
        }
        if (flagsPath == null || flagsPath.isBlank()) {
            flagsPath = "/flags";
        }
        boolean nameMissing = authHeaderName == null || authHeaderName.isBlank();
        boolean valueMissing = authHeaderValue == null || authHeaderValue.isBlank();
        if (nameMissing != valueMissing) {
            throw new IllegalArgumentException(
                    "authHeaderName and authHeaderValue must both be set (non-blank) or both be null/blank");
        }
        if (nameMissing) {
            // normalize blank → null so downstream code only needs a null check
            authHeaderName = null;
            authHeaderValue = null;
        }
        Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
        Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        Objects.requireNonNull(pollInterval, "pollInterval must not be null");
        Objects.requireNonNull(cacheTtl, "cacheTtl must not be null");
        if (connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
        if (requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        if (pollInterval.compareTo(MIN_POLL_INTERVAL) < 0) {
            throw new IllegalArgumentException(
                    "pollInterval must be >= " + MIN_POLL_INTERVAL + ", got " + pollInterval);
        }
        if (cacheTtl.compareTo(pollInterval) < 0) {
            throw new IllegalArgumentException(
                    "cacheTtl must be >= pollInterval");
        }
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "openflags-java";
        }
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException(
                    "failureThreshold must be > 0, got " + failureThreshold);
        }
        if (failureThreshold > MAX_FAILURE_THRESHOLD) {
            throw new IllegalArgumentException(
                    "failureThreshold must be <= " + MAX_FAILURE_THRESHOLD + ", got " + failureThreshold);
        }
        Objects.requireNonNull(maxBackoff, "maxBackoff must not be null");
        if (maxBackoff.isNegative() || maxBackoff.isZero()) {
            throw new IllegalArgumentException("maxBackoff must be positive");
        }
        if (maxBackoff.compareTo(pollInterval) < 0) {
            throw new IllegalArgumentException(
                    "maxBackoff must be greater than or equal to pollInterval");
        }
    }
}
