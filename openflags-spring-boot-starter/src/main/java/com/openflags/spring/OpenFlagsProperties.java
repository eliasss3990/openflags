package com.openflags.spring;

import com.openflags.provider.hybrid.SnapshotFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * The provider type to activate. Default: {@code "file"}.
     * <p>
     * Case-sensitive: must be one of {@code "file"}, {@code "remote"} or
     * {@code "hybrid"} (lowercase). Other values cause auto-configuration to fail.
     */
    private String provider = "file";

    /** File provider configuration. */
    private FileProperties file = new FileProperties();

    /** Remote provider configuration. Only consulted when {@code provider=remote}. */
    private RemoteProperties remote = new RemoteProperties();

    /** Hybrid provider configuration. Only consulted when {@code provider=hybrid}. */
    private HybridProperties hybrid = new HybridProperties();

    /** Metrics configuration for the Micrometer-based observability layer. */
    private final Metrics metrics = new Metrics();

    /** Audit configuration for evaluation MDC propagation. */
    private final Audit audit = new Audit();

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
     * Returns the hybrid provider configuration.
     *
     * @return the hybrid properties
     */
    public HybridProperties getHybrid() {
        return hybrid;
    }

    /**
     * Sets the hybrid provider configuration.
     *
     * @param hybrid the hybrid properties
     */
    public void setHybrid(HybridProperties hybrid) {
        this.hybrid = hybrid;
    }

    /**
     * Returns the metrics configuration.
     *
     * @return the metrics properties
     */
    public Metrics getMetrics() {
        return metrics;
    }

    /**
     * Returns the audit configuration.
     *
     * @return the audit properties
     */
    public Audit getAudit() {
        return audit;
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
         * Consecutive remote poll failures before exponential backoff kicks in.
         * Default: 5. Must be {@code >= 1} and {@code <= 100}.
         */
        private int failureThreshold = 5;

        /**
         * Upper bound for the backoff delay applied when the circuit is open.
         * Default: 5 minutes. Must be {@code >= pollInterval}.
         */
        private Duration maxBackoff = Duration.ofMinutes(5);

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

        /**
         * Returns the circuit breaker failure threshold.
         *
         * @return the failure threshold
         */
        public int getFailureThreshold() {
            return failureThreshold;
        }

        /**
         * Sets the circuit breaker failure threshold.
         *
         * @param failureThreshold the failure threshold
         */
        public void setFailureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        /**
         * Returns the maximum backoff delay applied when the circuit is open.
         *
         * @return the maximum backoff duration
         */
        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        /**
         * Sets the maximum backoff delay applied when the circuit is open.
         *
         * @param maxBackoff the maximum backoff duration
         */
        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }
    }

    /**
     * Configuration for the hybrid provider; only consulted when {@code provider=hybrid}.
     */
    public static class HybridProperties {

        /**
         * Filesystem path of the local snapshot file.
         * Required when {@code provider=hybrid}.
         */
        private String snapshotPath;

        /**
         * Snapshot format: {@code JSON} (default) or {@code YAML}.
         */
        private SnapshotFormat snapshotFormat = SnapshotFormat.JSON;

        /**
         * Enable filesystem watching of the snapshot for manual edits.
         * Default: {@code true}.
         */
        private boolean watchSnapshot = true;

        /**
         * Debounce window for ignoring self-induced file events.
         * Default: 500ms.
         */
        private Duration snapshotDebounce = Duration.ofMillis(500);

        /**
         * If {@code true}, fail initialization when neither remote nor snapshot can produce data.
         * Default: {@code false}.
         */
        private boolean failIfNoFallback = false;

        /**
         * Returns the snapshot file path.
         *
         * @return the snapshot path string, or null
         */
        public String getSnapshotPath() {
            return snapshotPath;
        }

        /**
         * Sets the snapshot file path.
         *
         * @param snapshotPath the snapshot path
         */
        public void setSnapshotPath(String snapshotPath) {
            this.snapshotPath = snapshotPath;
        }

        /**
         * Returns the snapshot format.
         *
         * @return the snapshot format
         */
        public SnapshotFormat getSnapshotFormat() {
            return snapshotFormat;
        }

        /**
         * Sets the snapshot format.
         *
         * @param snapshotFormat the snapshot format
         */
        public void setSnapshotFormat(SnapshotFormat snapshotFormat) {
            this.snapshotFormat = snapshotFormat;
        }

        /**
         * Returns whether snapshot watching is enabled.
         *
         * @return {@code true} if watch is enabled
         */
        public boolean isWatchSnapshot() {
            return watchSnapshot;
        }

        /**
         * Sets whether to watch the snapshot file.
         *
         * @param watchSnapshot {@code true} to enable watching
         */
        public void setWatchSnapshot(boolean watchSnapshot) {
            this.watchSnapshot = watchSnapshot;
        }

        /**
         * Returns the snapshot debounce window.
         *
         * @return the debounce duration
         */
        public Duration getSnapshotDebounce() {
            return snapshotDebounce;
        }

        /**
         * Sets the snapshot debounce window.
         *
         * @param snapshotDebounce the debounce duration
         */
        public void setSnapshotDebounce(Duration snapshotDebounce) {
            this.snapshotDebounce = snapshotDebounce;
        }

        /**
         * Returns whether to fail when no fallback is available.
         *
         * @return {@code true} to fail strictly
         */
        public boolean isFailIfNoFallback() {
            return failIfNoFallback;
        }

        /**
         * Sets whether to fail when no fallback is available.
         *
         * @param failIfNoFallback {@code true} to fail strictly
         */
        public void setFailIfNoFallback(boolean failIfNoFallback) {
            this.failIfNoFallback = failIfNoFallback;
        }
    }

    /**
     * Configuration for the Micrometer metrics integration.
     * <p>Activated only when {@code micrometer-core} is on the classpath and a
     * {@code MeterRegistry} bean is available.</p>
     */
    public static class Metrics {

        /**
         * Whether to enable Micrometer metrics. Default: {@code true}.
         * <p>Even when {@code true}, no metrics are emitted if {@code micrometer-core} is
         * absent or no {@code MeterRegistry} bean is exposed.</p>
         */
        private boolean enabled = true;

        /**
         * Whether to add the {@code flag} tag to per-flag counters and timers.
         * Disable to reduce cardinality when N &gt; 200 flags. Default: {@code true}.
         */
        private boolean tagFlagKey = true;

        /**
         * Static tags applied to every openflags metric. Useful for environment, region,
         * or service identification.
         */
        private Map<String, String> tags = new LinkedHashMap<>();

        /**
         * Returns whether metrics are enabled.
         *
         * @return {@code true} if enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether metrics are enabled.
         *
         * @param enabled {@code true} to enable
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns whether the {@code flag} tag is included on per-flag metrics.
         *
         * @return {@code true} if the flag key is tagged
         */
        public boolean isTagFlagKey() {
            return tagFlagKey;
        }

        /**
         * Sets whether the {@code flag} tag is included on per-flag metrics.
         *
         * @param tagFlagKey {@code true} to tag the flag key
         */
        public void setTagFlagKey(boolean tagFlagKey) {
            this.tagFlagKey = tagFlagKey;
        }

        /**
         * Returns the static tags applied to all openflags metrics.
         *
         * @return the static tags map
         */
        public Map<String, String> getTags() {
            return tags;
        }

        /**
         * Sets the static tags applied to all openflags metrics.
         *
         * @param tags the static tags map
         */
        public void setTags(Map<String, String> tags) {
            this.tags = tags;
        }
    }

    /**
     * Configuration for the audit integration that propagates evaluation context to
     * SLF4J MDC.
     */
    public static class Audit {

        /**
         * Whether to set {@code openflags.flag_key} and {@code openflags.targeting_key}
         * in SLF4J MDC during evaluation. Default: {@code false}.
         * <p><b>Warning:</b> the targeting key may contain PII; review your logging
         * pipeline before enabling.</p>
         */
        private boolean mdcEnabled = false;

        /**
         * Returns whether MDC propagation is enabled.
         *
         * @return {@code true} if MDC propagation is enabled
         */
        public boolean isMdcEnabled() {
            return mdcEnabled;
        }

        /**
         * Sets whether MDC propagation is enabled.
         *
         * @param mdcEnabled {@code true} to enable MDC propagation
         */
        public void setMdcEnabled(boolean mdcEnabled) {
            this.mdcEnabled = mdcEnabled;
        }
    }
}
