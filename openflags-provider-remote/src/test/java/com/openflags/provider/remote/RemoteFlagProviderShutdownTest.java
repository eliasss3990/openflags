package com.openflags.provider.remote;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link RemoteFlagProvider#shutdown()} respects the configured
 * {@code shutdownTimeout} and that the provider transitions to SHUTDOWN state
 * promptly.
 */
class RemoteFlagProviderShutdownTest {

    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody("{\"flags\":{}}")));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void shutdown_transitionsToShutdownState() {
        RemoteProviderConfig cfg = new RemoteProviderConfig(
                URI.create("http://localhost:" + wireMock.port()),
                "/flags", null, null,
                Duration.ofSeconds(5), Duration.ofSeconds(5),
                Duration.ofSeconds(30), Duration.ofMinutes(5),
                "test-agent",
                RemoteProviderConfig.DEFAULT_FAILURE_THRESHOLD,
                RemoteProviderConfig.DEFAULT_MAX_BACKOFF,
                RemoteProviderConfig.DEFAULT_MAX_RESPONSE_BYTES,
                Duration.ofSeconds(2),   // shutdownTimeout
                HttpVersion.AUTO);

        RemoteFlagProvider provider = new RemoteFlagProvider(cfg);
        provider.init();

        assertThat(provider.getState()).isEqualTo(com.openflags.core.provider.ProviderState.READY);

        long before = System.currentTimeMillis();
        provider.shutdown();
        long elapsed = System.currentTimeMillis() - before;

        assertThat(provider.getState()).isEqualTo(com.openflags.core.provider.ProviderState.SHUTDOWN);
        // Shutdown must complete well within 2× shutdownTimeout.
        assertThat(elapsed).isLessThan(4_000L);
    }

    @Test
    void shutdown_idempotent() {
        RemoteProviderConfig cfg = new RemoteProviderConfig(
                URI.create("http://localhost:" + wireMock.port()),
                "/flags", null, null,
                Duration.ofSeconds(5), Duration.ofSeconds(5),
                Duration.ofSeconds(30), Duration.ofMinutes(5),
                "test-agent");

        RemoteFlagProvider provider = new RemoteFlagProvider(cfg);
        provider.init();
        provider.shutdown();
        // Second call must not throw
        provider.shutdown();

        assertThat(provider.getState()).isEqualTo(com.openflags.core.provider.ProviderState.SHUTDOWN);
    }

    @Test
    void shutdownTimeout_defaultIs5Seconds() {
        RemoteProviderConfig cfg = RemoteProviderConfig.defaults(
                URI.create("http://localhost:" + wireMock.port()));
        assertThat(cfg.shutdownTimeout()).isEqualTo(Duration.ofSeconds(5));
    }
}
