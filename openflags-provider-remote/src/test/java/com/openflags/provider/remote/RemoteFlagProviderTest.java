package com.openflags.provider.remote;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.openflags.core.exception.ProviderException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteFlagProviderTest {

    private static final String FLAGS_JSON = """
            {
              "flags": {
                "dark-mode": {
                  "type": "boolean",
                  "value": true,
                  "enabled": true
                }
              }
            }
            """;

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

    private RemoteFlagProvider provider() {
        return RemoteFlagProviderBuilder
                .forUrl(URI.create("http://localhost:" + wireMock.port()))
                .pollInterval(Duration.ofSeconds(30))
                .cacheTtl(Duration.ofMinutes(5))
                .build();
    }

    @Test
    void init_success_stateReady_flagsExposed() {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FLAGS_JSON)));

        RemoteFlagProvider p = provider();
        p.init();

        assertThat(p.getState()).isEqualTo(ProviderState.READY);
        assertThat(p.getFlag("dark-mode")).isPresent();
        p.shutdown();
    }

    @Test
    void init_failure_throwsProviderException() {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(500).withBody("error")));

        RemoteFlagProvider p = provider();
        assertThatThrownBy(p::init)
                .isInstanceOf(ProviderException.class);

        assertThat(p.getState()).isEqualTo(ProviderState.NOT_READY);
        p.shutdown();
    }

    @Test
    void init_401_throwsAuthError() {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(401).withBody("unauthorized")));

        RemoteFlagProvider p = provider();
        assertThatThrownBy(p::init)
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Authentication failed")
                .hasMessageContaining("401");

        assertThat(p.getState()).isEqualTo(ProviderState.NOT_READY);
        p.shutdown();
    }

    @Test
    void init_403_throwsAuthError() {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(403).withBody("forbidden")));

        RemoteFlagProvider p = provider();
        assertThatThrownBy(p::init)
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Authentication failed")
                .hasMessageContaining("403");

        assertThat(p.getState()).isEqualTo(ProviderState.NOT_READY);
        p.shutdown();
    }

    @Test
    void getFlag_beforeInit_throwsIllegalStateException() {
        RemoteFlagProvider p = provider();
        assertThatThrownBy(() -> p.getFlag("any"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not initialized");
        p.shutdown();
    }

    @Test
    void shutdown_idempotent() {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FLAGS_JSON)));

        RemoteFlagProvider p = provider();
        p.init();
        p.shutdown();
        p.shutdown(); // should not throw

        assertThat(p.getState()).isEqualTo(ProviderState.SHUTDOWN);
    }

    @Test
    void init_idempotent_whenAlreadyReady() {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FLAGS_JSON)));

        RemoteFlagProvider p = provider();
        p.init();
        p.init(); // second call should be no-op
        assertThat(p.getState()).isEqualTo(ProviderState.READY);
        p.shutdown();
    }

    @Test
    void getAllFlags_returnsAllFlags() {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FLAGS_JSON)));

        RemoteFlagProvider p = provider();
        p.init();

        assertThat(p.getAllFlags()).containsKey("dark-mode");
        p.shutdown();
    }

    @Test
    void getFlag_afterShutdown_throwsIllegalStateException() {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FLAGS_JSON)));

        RemoteFlagProvider p = provider();
        p.init();
        p.shutdown();

        assertThatThrownBy(() -> p.getFlag("dark-mode"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shut down");
    }
}
