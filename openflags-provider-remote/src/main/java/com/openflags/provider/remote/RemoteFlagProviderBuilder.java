package com.openflags.provider.remote;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Fluent builder for {@link RemoteFlagProvider}. Mirrors the style of
 * {@code FileFlagProviderBuilder} from {@code openflags-provider-file}.
 */
public final class RemoteFlagProviderBuilder {

    private final URI baseUrl;
    private String flagsPath;
    private String authHeaderName;
    private String authHeaderValue;
    private Duration connectTimeout = RemoteProviderConfig.DEFAULT_CONNECT_TIMEOUT;
    private Duration requestTimeout = RemoteProviderConfig.DEFAULT_REQUEST_TIMEOUT;
    private Duration pollInterval = RemoteProviderConfig.DEFAULT_POLL_INTERVAL;
    private Duration cacheTtl = RemoteProviderConfig.DEFAULT_CACHE_TTL;
    private String userAgent;
    private int failureThreshold = RemoteProviderConfig.DEFAULT_FAILURE_THRESHOLD;
    private Duration maxBackoff = RemoteProviderConfig.DEFAULT_MAX_BACKOFF;
    private long maxResponseBytes = RemoteProviderConfig.DEFAULT_MAX_RESPONSE_BYTES;
    private Duration shutdownTimeout = RemoteProviderConfig.DEFAULT_SHUTDOWN_TIMEOUT;
    private HttpVersion httpVersion = RemoteProviderConfig.DEFAULT_HTTP_VERSION;

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
     * @param token the bearer token value (without the {@code "Bearer "} prefix);
     *              must not be null or blank
     * @return this builder
     * @throws NullPointerException     if {@code token} is null
     * @throws IllegalArgumentException if {@code token} is blank
     */
    public RemoteFlagProviderBuilder bearerToken(String token) {
        Objects.requireNonNull(token, "bearerToken must not be null");
        if (token.isBlank()) {
            throw new IllegalArgumentException("bearerToken must not be blank");
        }
        this.authHeaderName = "Authorization";
        this.authHeaderValue = "Bearer " + token;
        return this;
    }

    /**
     * Sets a custom authentication header.
     *
     * @param headerName the header name (e.g. {@code "X-API-Key"}); must not be null or blank
     * @param value      the header value; must not be null or blank
     * @return this builder
     * @throws NullPointerException     if either argument is null
     * @throws IllegalArgumentException if either argument is blank
     */
    public RemoteFlagProviderBuilder apiKey(String headerName, String value) {
        Objects.requireNonNull(headerName, "headerName must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (headerName.isBlank()) {
            throw new IllegalArgumentException("headerName must not be blank");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("apiKey value must not be blank");
        }
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
     * Sets the maximum number of bytes accepted in a single HTTP response body.
     * Responses larger than this cap are rejected with
     * {@link ResponseTooLargeException}.
     *
     * @param bytes the byte limit; must be positive
     * @return this builder
     */
    public RemoteFlagProviderBuilder maxResponseBytes(long bytes) {
        this.maxResponseBytes = bytes;
        return this;
    }

    /**
     * Sets the maximum time to wait for in-flight requests to complete when
     * {@link RemoteFlagProvider#shutdown()} is called.
     *
     * @param d the shutdown timeout; must be positive
     * @return this builder
     */
    public RemoteFlagProviderBuilder shutdownTimeout(Duration d) {
        this.shutdownTimeout = d;
        return this;
    }

    /**
     * Sets the desired HTTP protocol version for the underlying
     * {@link java.net.http.HttpClient}.
     *
     * @param version the version strategy; must not be null
     * @return this builder
     */
    public RemoteFlagProviderBuilder httpVersion(HttpVersion version) {
        this.httpVersion = version;
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
                maxBackoff,
                maxResponseBytes,
                shutdownTimeout,
                httpVersion);
        return new RemoteFlagProvider(cfg);
    }
}
