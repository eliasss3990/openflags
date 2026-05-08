package com.openflags.core.provider;

import java.time.Duration;

/**
 * Shared default values for remote-provider configuration.
 * <p>
 * Centralizing these constants in {@code openflags-core} (rather than in
 * {@code openflags-provider-remote}) lets the Spring Boot starter — which
 * declares the remote provider as an <em>optional</em> dependency — reference
 * them without forcing the remote module onto every consumer's classpath
 * (ADR-9).
 * </p>
 * <p>
 * {@code RemoteProviderConfig.DEFAULT_*} constants delegate to these values to
 * preserve the existing public API while removing duplicate literals.
 * </p>
 * <p>
 * <strong>Binary-compatibility note:</strong> {@code RemoteProviderConfig}'s
 * {@code public static final} fields, after this delegation, are no longer
 * compile-time constants (JLS §15.29) and will not be inlined into newly-compiled
 * callers. Existing callers compiled against the prior literal-valued constants
 * keep their inlined values; since those values are identical to the ones declared
 * here, no behavioral drift is observable. The values published in this class are
 * intended to be stable across minor versions.
 * </p>
 */
public final class RemoteDefaults {

    /** Maximum allowed value for {@code failureThreshold}. */
    public static final int MAX_FAILURE_THRESHOLD = 100;

    /** Default circuit-breaker failure threshold. */
    public static final int DEFAULT_FAILURE_THRESHOLD = 5;

    /** Default maximum exponential-backoff cap. */
    public static final Duration DEFAULT_MAX_BACKOFF = Duration.ofMinutes(5);

    /** Default HTTP connect timeout. */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** Default HTTP request timeout (whole-request, including body). */
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /** Default poll interval between remote refreshes. */
    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(30);

    /** Default in-memory cache TTL for remote responses. */
    public static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);

    /** Default {@code User-Agent} header value sent on remote requests. */
    public static final String DEFAULT_USER_AGENT = "openflags-java";

    /** Default path appended to {@code base-url} when fetching the flag set. */
    public static final String DEFAULT_FLAGS_PATH = "/flags";

    /** Default cap on remote response size (10 MiB). */
    public static final long DEFAULT_MAX_RESPONSE_BYTES = 10L * 1024 * 1024;

    /** Default await timeout used by the remote provider during shutdown. */
    public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private RemoteDefaults() {
        // utility
    }
}
