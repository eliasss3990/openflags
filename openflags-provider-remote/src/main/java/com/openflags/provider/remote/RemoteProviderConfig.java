package com.openflags.provider.remote;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for a {@link RemoteFlagProvider}.
 *
 * @param baseUrl         the base URL of the backend (e.g. {@code https://flags.example.com}); non-null,
 *                        scheme must be {@code http} or {@code https}
 * @param flagsPath       the path appended to {@code baseUrl} for fetching flags; default {@code "/flags"}
 * @param authHeaderName  the HTTP header used for authentication (e.g. {@code "Authorization"} or
 *                        {@code "X-API-Key"}); may be null to disable auth
 * @param authHeaderValue the literal header value (e.g. {@code "Bearer eyJ..."}); may be null
 *                        if {@code authHeaderName} is null. Never logged.
 * @param connectTimeout  HTTP connect timeout; must be positive
 * @param requestTimeout  HTTP request timeout (per request); must be positive
 * @param pollInterval    interval between polls; must be {@code >= 5s}
 * @param cacheTtl        cache TTL after which the provider transitions to {@code ERROR} state
 *                        if no successful fetch happens; must be {@code >= pollInterval}
 * @param userAgent       value of the {@code User-Agent} header; non-blank, default
 *                        {@code "openflags-java"}
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
        String userAgent
) {

    /** Minimum allowed poll interval to prevent accidental backend overload. */
    public static final Duration MIN_POLL_INTERVAL = Duration.ofSeconds(5);

    /**
     * Compact constructor that validates fields and applies defaults to optional ones.
     *
     * @throws NullPointerException     if any required field is null
     * @throws IllegalArgumentException if any range/scheme constraint is violated
     */
    public RemoteProviderConfig {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        String scheme = baseUrl.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
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
    }
}
