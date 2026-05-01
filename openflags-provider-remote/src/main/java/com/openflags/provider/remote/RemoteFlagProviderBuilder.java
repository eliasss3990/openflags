package com.openflags.provider.remote;

import java.net.URI;
import java.time.Duration;

/**
 * Fluent builder for {@link RemoteFlagProvider}. Mirrors the style of
 * {@code FileFlagProviderBuilder} from {@code openflags-provider-file}.
 */
public final class RemoteFlagProviderBuilder {

    private final URI baseUrl;
    private String flagsPath;
    private String authHeaderName;
    private String authHeaderValue;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(10);
    private Duration pollInterval = Duration.ofSeconds(30);
    private Duration cacheTtl = Duration.ofMinutes(5);
    private String userAgent;
    private int failureThreshold = 5;
    private Duration maxBackoff = Duration.ofMinutes(5);

    /**
     * Creates a builder for the given base URL.
     *
     * @param baseUrl the base URL of the remote flags backend
     * @return a new builder
     */
    public static RemoteFlagProviderBuilder forUrl(URI baseUrl) {
        return new RemoteFlagProviderBuilder(baseUrl);
    }

    /**
     * Creates a builder for the given base URL string.
     *
     * @param baseUrl the base URL string
     * @return a new builder
     */
    public static RemoteFlagProviderBuilder forUrl(String baseUrl) {
        return forUrl(URI.create(baseUrl));
    }

    private RemoteFlagProviderBuilder(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Sets the path appended to the base URL for fetching flags.
     *
     * @param path the flags path (e.g. {@code "/api/v1/flags"})
     * @return this builder
     */
    public RemoteFlagProviderBuilder flagsPath(String path) {
        this.flagsPath = path;
        return this;
    }

    /**
     * Sets a Bearer token for authentication.
     * Shortcut for {@code apiKey("Authorization", "Bearer " + token)}.
     *
     * @param token the bearer token value (without the "Bearer " prefix)
     * @return this builder
     */
    public RemoteFlagProviderBuilder bearerToken(String token) {
        this.authHeaderName = "Authorization";
        this.authHeaderValue = "Bearer " + token;
        return this;
    }

    /**
     * Sets a custom authentication header.
     *
     * @param headerName the header name (e.g. {@code "X-API-Key"})
     * @param value      the header value
     * @return this builder
     */
    public RemoteFlagProviderBuilder apiKey(String headerName, String value) {
        this.authHeaderName = headerName;
        this.authHeaderValue = value;
        return this;
    }

    /**
     * Sets the HTTP connect timeout.
     *
     * @param d the duration; must be positive
     * @return this builder
     */
    public RemoteFlagProviderBuilder connectTimeout(Duration d) {
        this.connectTimeout = d;
        return this;
    }

    /**
     * Sets the HTTP request timeout per request.
     *
     * @param d the duration; must be positive
     * @return this builder
     */
    public RemoteFlagProviderBuilder requestTimeout(Duration d) {
        this.requestTimeout = d;
        return this;
    }

    /**
     * Sets the polling interval.
     *
     * @param d the duration; must be {@code >= 5s}
     * @return this builder
     */
    public RemoteFlagProviderBuilder pollInterval(Duration d) {
        this.pollInterval = d;
        return this;
    }

    /**
     * Sets the cache TTL.
     *
     * @param d the duration; must be {@code >= pollInterval}
     * @return this builder
     */
    public RemoteFlagProviderBuilder cacheTtl(Duration d) {
        this.cacheTtl = d;
        return this;
    }

    /**
     * Sets the User-Agent header value.
     *
     * @param userAgent the user agent string
     * @return this builder
     */
    public RemoteFlagProviderBuilder userAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    /**
     * Sets the number of consecutive poll failures required before the
     * circuit breaker opens and the poll interval starts growing
     * exponentially.
     *
     * @param threshold the failure threshold; must be in {@code [1, 100]}
     * @return this builder
     */
    public RemoteFlagProviderBuilder failureThreshold(int threshold) {
        this.failureThreshold = threshold;
        return this;
    }

    /**
     * Sets the maximum delay applied between polls when the circuit
     * breaker is open.
     *
     * @param d the maximum backoff; must be positive and {@code >= pollInterval}
     * @return this builder
     */
    public RemoteFlagProviderBuilder maxBackoff(Duration d) {
        this.maxBackoff = d;
        return this;
    }

    /**
     * Builds a configured but un-initialized {@link RemoteFlagProvider}.
     * Caller must invoke {@link RemoteFlagProvider#init()}.
     *
     * @return a new {@link RemoteFlagProvider}
     */
    public RemoteFlagProvider build() {
        RemoteProviderConfig cfg = new RemoteProviderConfig(
                baseUrl,
                flagsPath,
                authHeaderName,
                authHeaderValue,
                connectTimeout,
                requestTimeout,
                pollInterval,
                cacheTtl,
                userAgent,
                failureThreshold,
                maxBackoff);
        return new RemoteFlagProvider(cfg);
    }
}
