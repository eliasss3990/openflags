package com.openflags.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Configuration properties for the openflags Spring Boot integration.
 * <p>
 * Bound to the {@code openflags} prefix in {@code application.yml} or
 * {@code application.properties}.
 * </p>
 *
 * <pre>
 * openflags:
 *   provider: file
 *   file:
 *     path: classpath:flags.yml
 *     watch-enabled: true
 * </pre>
 */
@ConfigurationProperties(prefix = "openflags")
public class OpenFlagsProperties {

    /** The provider type to activate. Default: {@code "file"}. */
    private String provider = "file";

    /** File provider configuration. */
    private FileProperties file = new FileProperties();

    /** Remote provider configuration. Only consulted when {@code provider=remote}. */
    private RemoteProperties remote = new RemoteProperties();

    /**
     * Returns the active provider type.
     *
     * @return the provider type string
     */
    public String getProvider() {
        return provider;
    }

    /**
     * Sets the active provider type.
     *
     * @param provider the provider type
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * Returns the file provider configuration.
     *
     * @return the file properties
     */
    public FileProperties getFile() {
        return file;
    }

    /**
     * Sets the file provider configuration.
     *
     * @param file the file properties
     */
    public void setFile(FileProperties file) {
        this.file = file;
    }

    /**
     * Returns the remote provider configuration.
     *
     * @return the remote properties
     */
    public RemoteProperties getRemote() {
        return remote;
    }

    /**
     * Sets the remote provider configuration.
     *
     * @param remote the remote properties
     */
    public void setRemote(RemoteProperties remote) {
        this.remote = remote;
    }

    /**
     * File provider specific configuration.
     */
    public static class FileProperties {

        /**
         * Path to the flags definition file.
         * Supports {@code classpath:} and {@code file:} prefixes.
         * Default: {@code "classpath:flags.yml"}.
         */
        private String path = "classpath:flags.yml";

        /**
         * Whether to enable hot reload via file watching.
         * Automatically disabled when the file is inside a JAR.
         * Default: {@code true}.
         */
        private boolean watchEnabled = true;

        /**
         * Returns the path to the flag file.
         *
         * @return the path string
         */
        public String getPath() {
            return path;
        }

        /**
         * Sets the path to the flag file.
         *
         * @param path the path string
         */
        public void setPath(String path) {
            this.path = path;
        }

        /**
         * Returns whether file watching is enabled.
         *
         * @return {@code true} if watch is enabled
         */
        public boolean isWatchEnabled() {
            return watchEnabled;
        }

        /**
         * Sets whether file watching is enabled.
         *
         * @param watchEnabled {@code true} to enable
         */
        public void setWatchEnabled(boolean watchEnabled) {
            this.watchEnabled = watchEnabled;
        }
    }

    /**
     * Configuration for the remote provider; only consulted when {@code provider=remote}.
     */
    public static class RemoteProperties {

        /** Base URL of the remote flags backend. */
        private URI baseUrl;

        /** Path appended to the base URL for fetching flags. Default: {@code "/flags"}. */
        private String flagsPath = "/flags";

        /** HTTP header name for authentication, e.g. {@code "X-API-Key"}. May be null. */
        private String authHeaderName;

        /** HTTP header value for authentication. May be null if {@code authHeaderName} is null. */
        private String authHeaderValue;

        /** HTTP connect timeout. Default: 5 seconds. */
        private Duration connectTimeout = Duration.ofSeconds(5);

        /** HTTP request timeout. Default: 10 seconds. */
        private Duration requestTimeout = Duration.ofSeconds(10);

        /** Polling interval. Default: 30 seconds. Minimum: 5 seconds. */
        private Duration pollInterval = Duration.ofSeconds(30);

        /** Cache TTL. Default: 5 minutes. Must be {@code >= pollInterval}. */
        private Duration cacheTtl = Duration.ofMinutes(5);

        /** User-Agent header value. Defaults to {@code "openflags-java"} if blank. */
        private String userAgent;

        /**
         * Returns the base URL.
         *
         * @return the base URL
         */
        public URI getBaseUrl() {
            return baseUrl;
        }

        /**
         * Sets the base URL.
         *
         * @param baseUrl the base URL
         */
        public void setBaseUrl(URI baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * Returns the flags path.
         *
         * @return the flags path
         */
        public String getFlagsPath() {
            return flagsPath;
        }

        /**
         * Sets the flags path.
         *
         * @param flagsPath the flags path
         */
        public void setFlagsPath(String flagsPath) {
            this.flagsPath = flagsPath;
        }

        /**
         * Returns the auth header name.
         *
         * @return the auth header name, or null
         */
        public String getAuthHeaderName() {
            return authHeaderName;
        }

        /**
         * Sets the auth header name.
         *
         * @param authHeaderName the auth header name
         */
        public void setAuthHeaderName(String authHeaderName) {
            this.authHeaderName = authHeaderName;
        }

        /**
         * Returns the auth header value.
         *
         * @return the auth header value, or null
         */
        public String getAuthHeaderValue() {
            return authHeaderValue;
        }

        /**
         * Sets the auth header value.
         *
         * @param authHeaderValue the auth header value
         */
        public void setAuthHeaderValue(String authHeaderValue) {
            this.authHeaderValue = authHeaderValue;
        }

        /**
         * Returns the connect timeout.
         *
         * @return the connect timeout
         */
        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        /**
         * Sets the connect timeout.
         *
         * @param connectTimeout the connect timeout
         */
        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        /**
         * Returns the request timeout.
         *
         * @return the request timeout
         */
        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        /**
         * Sets the request timeout.
         *
         * @param requestTimeout the request timeout
         */
        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        /**
         * Returns the poll interval.
         *
         * @return the poll interval
         */
        public Duration getPollInterval() {
            return pollInterval;
        }

        /**
         * Sets the poll interval.
         *
         * @param pollInterval the poll interval
         */
        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        /**
         * Returns the cache TTL.
         *
         * @return the cache TTL
         */
        public Duration getCacheTtl() {
            return cacheTtl;
        }

        /**
         * Sets the cache TTL.
         *
         * @param cacheTtl the cache TTL
         */
        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }

        /**
         * Returns the User-Agent string.
         *
         * @return the user agent string, or null
         */
        public String getUserAgent() {
            return userAgent;
        }

        /**
         * Sets the User-Agent string.
         *
         * @param userAgent the user agent string
         */
        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }
}
