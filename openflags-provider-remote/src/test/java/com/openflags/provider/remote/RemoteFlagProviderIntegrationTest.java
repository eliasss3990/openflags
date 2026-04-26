package com.openflags.provider.remote;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.openflags.core.event.ChangeType;
import com.openflags.core.event.FlagChangeEvent;
import com.openflags.core.provider.ProviderState;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class RemoteFlagProviderIntegrationTest {

    private static final String FLAGS_A_JSON = """
            {
              "flags": {
                "flag-a": {
                  "type": "boolean",
                  "value": true,
                  "enabled": true
                }
              }
            }
            """;

    private static final String FLAGS_B_JSON = """
            {
              "flags": {
                "flag-a": {
                  "type": "boolean",
                  "value": false,
                  "enabled": true
                },
                "flag-b": {
                  "type": "string",
                  "value": "new",
                  "enabled": true
                }
              }
            }
            """;

    private static final String FLAGS_MULTIVARIANT_JSON = """
            {
              "flags": {
                "checkout-experiment": {
                  "type": "string",
                  "value": "control",
                  "enabled": true,
                  "rules": [
                    {
                      "name": "ab-test",
                      "kind": "multivariant",
                      "variants": [
                        { "value": "control",   "weight": 50 },
                        { "value": "treatment", "weight": 50 }
                      ]
                    }
                  ]
                }
              }
            }
            """;

    // Minimum pollInterval allowed by RemoteProviderConfig validation
    private static final Duration POLL = Duration.ofSeconds(5);
    // TTL long enough to not expire during test
    private static final Duration TTL  = Duration.ofSeconds(60);
    // How long to wait for state transitions
    private static final int AWAIT_SECONDS = 20;

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
                .pollInterval(POLL)
                .cacheTtl(TTL)
                .build();
    }

    @Test
    void readyToDegradedToReady_onBackendRecovery() {
        String scenario = "flap";
        wireMock.stubFor(get(urlEqualTo("/flags")).inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FLAGS_A_JSON))
                .willSetStateTo("DOWN"));

        wireMock.stubFor(get(urlEqualTo("/flags")).inScenario(scenario)
                .whenScenarioStateIs("DOWN")
                .willReturn(aResponse().withStatus(503).withBody("Service Unavailable"))
                .willSetStateTo("UP"));

        wireMock.stubFor(get(urlEqualTo("/flags")).inScenario(scenario)
                .whenScenarioStateIs("UP")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FLAGS_A_JSON)));

        RemoteFlagProvider p = provider();
        p.init();

        assertThat(p.getState()).isEqualTo(ProviderState.READY);

        // wait for DEGRADED after 503
        Awaitility.await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> p.getState() == ProviderState.DEGRADED);

        // cache still served during DEGRADED
        assertThat(p.getFlag("flag-a")).isPresent();

        // wait for READY after recovery
        Awaitility.await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> p.getState() == ProviderState.READY);

        p.shutdown();
    }

    @Test
    void staleCacheServedWhileDegraded() {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .inScenario("fail")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FLAGS_A_JSON))
                .willSetStateTo("FAIL"));

        wireMock.stubFor(get(urlEqualTo("/flags"))
                .inScenario("fail")
                .whenScenarioStateIs("FAIL")
                .willReturn(aResponse().withStatus(500)));

        RemoteFlagProvider p = provider();
        p.init();

        Awaitility.await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> p.getState() == ProviderState.DEGRADED);

        // stale cache still served
        assertThat(p.getFlag("flag-a")).isPresent();
        assertThat(p.getFlag("flag-a").get().value().asBoolean()).isTrue();

        p.shutdown();
    }

    @Test
    void flagChangeEvents_addedRemovedChanged() {
        List<FlagChangeEvent> events = new ArrayList<>();

        wireMock.stubFor(get(urlEqualTo("/flags"))
                .inScenario("update")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FLAGS_A_JSON))
                .willSetStateTo("UPDATED"));

        wireMock.stubFor(get(urlEqualTo("/flags"))
                .inScenario("update")
                .whenScenarioStateIs("UPDATED")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FLAGS_B_JSON)));

        RemoteFlagProvider p = provider();
        p.addChangeListener(events::add);
        p.init();

        // wait for second poll to trigger events
        Awaitility.await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> events.stream().anyMatch(e -> e.flagKey().equals("flag-b")));

        List<ChangeType> changeTypes = events.stream().map(FlagChangeEvent::changeType).toList();
        assertThat(changeTypes).contains(ChangeType.CREATED); // flag-b added
        assertThat(changeTypes).contains(ChangeType.UPDATED); // flag-a changed value

        p.shutdown();
    }

    @Test
    void shutdown_releasesSchedulerThreads() throws InterruptedException {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FLAGS_A_JSON)));

        RemoteFlagProvider p = provider();
        p.init();

        // verify poller thread exists
        long pollersBefore = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().contains("openflags-remote-poller"))
                .count();
        assertThat(pollersBefore).isGreaterThan(0);

        p.shutdown();

        // give threads time to stop
        Thread.sleep(500);

        long pollersAfter = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().contains("openflags-remote-poller") && t.isAlive())
                .count();
        assertThat(pollersAfter).isZero();
    }

    @Test
    void multivariantFlag_servedFromRemote() {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FLAGS_MULTIVARIANT_JSON)));

        RemoteFlagProvider p = provider();
        p.init();

        assertThat(p.getFlag("checkout-experiment")).isPresent();
        assertThat(p.getFlag("checkout-experiment").get().rules()).hasSize(1);

        p.shutdown();
    }
}
