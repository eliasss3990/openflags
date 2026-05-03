package com.openflags.provider.remote;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that the 11-arg convenience constructor (non-deprecated) and the
 * legacy 9-arg {@code @Deprecated} constructor still compile and produce
 * sensible defaults for the fields added in this release.
 */
@SuppressWarnings("deprecation")
class RemoteProviderConfigCompatTest {

    private static final URI BASE = URI.create("https://flags.example.com");
    private static final Duration D5  = Duration.ofSeconds(5);
    private static final Duration D30 = Duration.ofSeconds(30);
    private static final Duration D5M = Duration.ofMinutes(5);

    @Test
    void elevenArgConstructor_populatesNewFieldsWithDefaults() {
        RemoteProviderConfig cfg = new RemoteProviderConfig(
                BASE, "/flags", null, null,
                D5, D5, D30, D5M,
                "test-agent",
                RemoteProviderConfig.DEFAULT_FAILURE_THRESHOLD,
                RemoteProviderConfig.DEFAULT_MAX_BACKOFF);

        assertThat(cfg.maxResponseBytes()).isEqualTo(RemoteProviderConfig.DEFAULT_MAX_RESPONSE_BYTES);
        assertThat(cfg.shutdownTimeout()).isEqualTo(RemoteProviderConfig.DEFAULT_SHUTDOWN_TIMEOUT);
        assertThat(cfg.httpVersion()).isEqualTo(HttpVersion.AUTO);
    }

    @Test
    void elevenArgConstructor_preservesAllExplicitFields() {
        RemoteProviderConfig cfg = new RemoteProviderConfig(
                BASE, "/custom", "X-API-Key", "secret",
                Duration.ofSeconds(3), Duration.ofSeconds(7),
                Duration.ofSeconds(10), Duration.ofMinutes(2),
                "my-agent", 3, Duration.ofMinutes(10));

        assertThat(cfg.baseUrl()).isEqualTo(BASE);
        assertThat(cfg.flagsPath()).isEqualTo("/custom");
        assertThat(cfg.authHeaderName()).isEqualTo("X-API-Key");
        assertThat(cfg.authHeaderValue()).isEqualTo("secret");
        assertThat(cfg.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(cfg.requestTimeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(cfg.pollInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(cfg.cacheTtl()).isEqualTo(Duration.ofMinutes(2));
        assertThat(cfg.userAgent()).isEqualTo("my-agent");
        assertThat(cfg.failureThreshold()).isEqualTo(3);
        assertThat(cfg.maxBackoff()).isEqualTo(Duration.ofMinutes(10));
        // new fields get defaults
        assertThat(cfg.maxResponseBytes()).isEqualTo(RemoteProviderConfig.DEFAULT_MAX_RESPONSE_BYTES);
        assertThat(cfg.shutdownTimeout()).isEqualTo(RemoteProviderConfig.DEFAULT_SHUTDOWN_TIMEOUT);
        assertThat(cfg.httpVersion()).isEqualTo(HttpVersion.AUTO);
    }

    @Test
    void nineArgDeprecatedConstructor_stillWorks() {
        RemoteProviderConfig cfg = new RemoteProviderConfig(
                BASE, "/flags", null, null,
                D5, D5, D30, D5M,
                "legacy-agent");

        assertThat(cfg.userAgent()).isEqualTo("legacy-agent");
        assertThat(cfg.failureThreshold()).isEqualTo(RemoteProviderConfig.DEFAULT_FAILURE_THRESHOLD);
        assertThat(cfg.maxResponseBytes()).isEqualTo(RemoteProviderConfig.DEFAULT_MAX_RESPONSE_BYTES);
        assertThat(cfg.shutdownTimeout()).isEqualTo(RemoteProviderConfig.DEFAULT_SHUTDOWN_TIMEOUT);
        assertThat(cfg.httpVersion()).isEqualTo(HttpVersion.AUTO);
    }

    @Test
    void thirteenArgCanonicalConstructor_roundTripsAllFields() {
        RemoteProviderConfig cfg = new RemoteProviderConfig(
                BASE, "/flags", null, null,
                D5, D5, D30, D5M,
                "agent", 2, D5M,
                512_000L, Duration.ofSeconds(3), HttpVersion.HTTP_1_1);

        assertThat(cfg.maxResponseBytes()).isEqualTo(512_000L);
        assertThat(cfg.shutdownTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(cfg.httpVersion()).isEqualTo(HttpVersion.HTTP_1_1);
    }

    @Test
    void defaultsFactory_populatesNewFields() {
        RemoteProviderConfig cfg = RemoteProviderConfig.defaults(BASE);
        assertThat(cfg.maxResponseBytes()).isEqualTo(RemoteProviderConfig.DEFAULT_MAX_RESPONSE_BYTES);
        assertThat(cfg.shutdownTimeout()).isEqualTo(RemoteProviderConfig.DEFAULT_SHUTDOWN_TIMEOUT);
        assertThat(cfg.httpVersion()).isEqualTo(HttpVersion.AUTO);
    }

    @Test
    void defaultMaxResponseBytes_is10Mib() {
        assertThat(RemoteProviderConfig.DEFAULT_MAX_RESPONSE_BYTES)
                .isEqualTo(10L * 1024 * 1024);
    }

    @Test
    void defaultShutdownTimeout_is5Seconds() {
        assertThat(RemoteProviderConfig.DEFAULT_SHUTDOWN_TIMEOUT)
                .isEqualTo(Duration.ofSeconds(5));
    }
}
