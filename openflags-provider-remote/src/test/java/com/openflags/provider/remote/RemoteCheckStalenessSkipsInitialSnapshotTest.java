package com.openflags.provider.remote;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.openflags.core.provider.ProviderState;
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
 * Verifies that {@code checkStaleness()} skips the TTL computation when the
 * snapshot carries the {@code Instant.EPOCH} sentinel (no successful fetch yet),
 * preventing a spurious transition to {@link ProviderState#ERROR} before the
 * first poll.
 */
class RemoteCheckStalenessSkipsInitialSnapshotTest {

    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void checkStaleness_epochSentinel_doesNotTransitionToError() {
        // The Instant.EPOCH sentinel produces an age of ~55 years, which would always
        // exceed any reasonable cacheTtl (e.g. 5 min) and cause a spurious ERROR
        // transition if the guard were absent.
        RemoteFlagProvider p = RemoteFlagProviderBuilder
                .forUrl(URI.create("http://localhost:19999"))
                .build(); // defaults: pollInterval=30s, cacheTtl=5min

        p.checkStaleness(); // must return early — no successful fetch has happened

        assertThat(p.getState()).isEqualTo(ProviderState.NOT_READY);
        p.shutdown();
    }

    @Test
    void checkStaleness_shutdownState_doesNotTransitionState() {
        RemoteFlagProvider p = RemoteFlagProviderBuilder
                .forUrl(URI.create("http://localhost:19999"))
                .build();
        p.shutdown(); // state = SHUTDOWN

        p.checkStaleness(); // existing SHUTDOWN guard must return early

        assertThat(p.getState()).isEqualTo(ProviderState.SHUTDOWN);
    }

    @Test
    void checkStaleness_readyState_transitionsToDegraded() {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "flags": { "x": { "type": "boolean", "value": true, "enabled": true } } }
                                """)));

        RemoteFlagProvider p = RemoteFlagProviderBuilder
                .forUrl(URI.create("http://localhost:" + wireMock.port()))
                .build(); // defaults: pollInterval=30s, cacheTtl=5min
        p.init();

        // fetchedAt is now(); age ≈ 0 ms < 5 min TTL → should degrade, not error
        p.checkStaleness();

        assertThat(p.getState()).isEqualTo(ProviderState.DEGRADED);
        p.shutdown();
    }
}
