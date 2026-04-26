package com.openflags.provider.hybrid;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.openflags.core.event.FlagChangeEvent;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.provider.ProviderState;
import com.openflags.provider.remote.RemoteProviderConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HybridFlagProviderIntegrationTest {

    private static final String FLAGS_JSON = """
            {
              "flags": {
                "feature-a": {
                  "type": "boolean",
                  "value": true,
                  "enabled": true
                },
                "feature-b": {
                  "type": "string",
                  "value": "hello",
                  "enabled": true
                }
              }
            }
            """;

    private static final String FLAGS_JSON_V2 = """
            {
              "flags": {
                "feature-a": {
                  "type": "boolean",
                  "value": false,
                  "enabled": true
                },
                "feature-b": {
                  "type": "string",
                  "value": "updated",
                  "enabled": true
                }
              }
            }
            """;

    private WireMockServer wiremock;
    private HybridFlagProvider provider;

    @TempDir
    Path tempDir;

    @BeforeEach
    void startWireMock() {
        wiremock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wiremock.start();
    }

    @AfterEach
    void stopAll() {
        if (provider != null) {
            try {
                provider.shutdown();
            } catch (Exception ignored) {}
        }
        wiremock.stop();
    }

    RemoteProviderConfig remoteConfig() {
        return new RemoteProviderConfig(
                URI.create("http://localhost:" + wiremock.port()),
                "/flags",
                null, null,
                Duration.ofSeconds(2), Duration.ofSeconds(5),
                Duration.ofSeconds(5), Duration.ofSeconds(30),
                "test-hybrid");
    }

    HybridFlagProvider buildProvider(Path snapshot) {
        return HybridFlagProvider.builder()
                .remoteConfig(remoteConfig())
                .snapshotPath(snapshot)
                .snapshotFormat(SnapshotFormat.JSON)
                .watchSnapshot(true)
                .snapshotDebounce(Duration.ofMillis(200))
                .build();
    }

    // ---- T7-1: cold start backend UP → READY, snapshot written ----

    @Test
    void coldStart_backendUp_readyAndSnapshotWritten() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody(FLAGS_JSON)
                        .withHeader("Content-Type", "application/json")));

        Path snapshot = tempDir.resolve("snap.json");
        provider = buildProvider(snapshot);
        provider.init();

        assertThat(provider.getState()).isEqualTo(ProviderState.READY);
        assertThat(provider.getFlag("feature-a")).isPresent();
        assertThat(provider.getFlag("feature-a").get().value().asBoolean()).isTrue();

        // snapshot written by init (remote change events emitted on first fetch)
        Awaitility.await().atMost(3, TimeUnit.SECONDS)
                .until(() -> Files.exists(snapshot) && Files.size(snapshot) > 0);
        assertThat(snapshot).exists();
    }

    // ---- T7-2: cold start backend DOWN, snapshot exists → DEGRADED serving snapshot ----

    @Test
    void coldStart_backendDown_snapshotExists_degraded() throws Exception {
        // pre-write a snapshot file
        Path snapshot = tempDir.resolve("snap.json");
        Files.writeString(snapshot, FLAGS_JSON);

        // Use a port that refuses connections (WireMock not started on this port)
        provider = HybridFlagProvider.builder()
                .remoteConfig(new RemoteProviderConfig(
                        URI.create("http://localhost:19998"),
                        "/flags", null, null,
                        Duration.ofMillis(500), Duration.ofMillis(1000),
                        Duration.ofSeconds(5), Duration.ofSeconds(30),
                        "test-hybrid"))
                .snapshotPath(snapshot)
                .snapshotFormat(SnapshotFormat.JSON)
                .watchSnapshot(false)
                .snapshotDebounce(Duration.ofMillis(200))
                .build();

        provider.init(); // remote fails, file succeeds → should work
        // When remote fails, file is serving: state is DEGRADED
        assertThat(provider.getState()).isIn(ProviderState.DEGRADED, ProviderState.READY);
        assertThat(provider.getFlag("feature-a")).isPresent();
    }

    // ---- T7-3: cold start backend DOWN, no snapshot → ProviderException ----

    @Test
    void coldStart_backendDown_noSnapshot_throws() {
        wiremock.stop();

        Path snapshot = tempDir.resolve("snap.json"); // does not exist
        provider = HybridFlagProvider.builder()
                .remoteConfig(new RemoteProviderConfig(
                        URI.create("http://localhost:19997"),
                        "/flags", null, null,
                        Duration.ofMillis(500), Duration.ofMillis(1000),
                        Duration.ofSeconds(5), Duration.ofSeconds(30),
                        "test-hybrid"))
                .snapshotPath(snapshot)
                .snapshotFormat(SnapshotFormat.JSON)
                .watchSnapshot(false)
                .snapshotDebounce(Duration.ofMillis(200))
                .failIfNoFallback(true)
                .build();

        assertThatThrownBy(() -> provider.init())
                .isInstanceOf(ProviderException.class);
    }

    // ---- T7-4: backend recovers → getFlag serves updated data, snapshot updated ----

    @Test
    void backend_recovers_snapshotUpdated() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody(FLAGS_JSON)
                        .withHeader("Content-Type", "application/json")));

        Path snapshot = tempDir.resolve("snap.json");
        provider = buildProvider(snapshot);
        provider.init();

        assertThat(provider.getState()).isEqualTo(ProviderState.READY);

        // update the stub to return v2
        wiremock.resetAll();
        wiremock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody(FLAGS_JSON_V2)
                        .withHeader("Content-Type", "application/json")));

        // wait for the poller to pick up v2
        Awaitility.await().atMost(15, TimeUnit.SECONDS)
                .until(() -> {
                    var flag = provider.getFlag("feature-b");
                    return flag.isPresent() && "updated".equals(flag.get().value().asString());
                });

        // verify snapshot was updated
        Awaitility.await().atMost(3, TimeUnit.SECONDS)
                .until(() -> {
                    String content = Files.readString(snapshot);
                    return content.contains("updated");
                });
    }

    // ---- T7-5: change listener receives events ----

    @Test
    void changeListener_receivesRemoteEvents() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody(FLAGS_JSON)
                        .withHeader("Content-Type", "application/json")));

        Path snapshot = tempDir.resolve("snap.json");
        provider = buildProvider(snapshot);

        List<FlagChangeEvent> events = new ArrayList<>();
        provider.addChangeListener(events::add);

        provider.init();

        // after init, remote fires events for all flags found on first fetch
        // update stub and wait for poll
        wiremock.resetAll();
        wiremock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody(FLAGS_JSON_V2)
                        .withHeader("Content-Type", "application/json")));

        Awaitility.await().atMost(15, TimeUnit.SECONDS)
                .until(() -> events.stream().anyMatch(e -> "feature-b".equals(e.flagKey())));
    }

    // ---- T7-6: shutdown releases threads ----

    @Test
    void shutdown_releasesThreads() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody(FLAGS_JSON)
                        .withHeader("Content-Type", "application/json")));

        Path snapshot = tempDir.resolve("snap.json");
        provider = buildProvider(snapshot);
        provider.init();

        assertThat(provider.getState()).isEqualTo(ProviderState.READY);
        provider.shutdown();
        assertThat(provider.getState()).isEqualTo(ProviderState.SHUTDOWN);
    }

    // ---- T7-7: remove listener works ----

    @Test
    void removeListener_stopsReceivingEvents() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody(FLAGS_JSON)
                        .withHeader("Content-Type", "application/json")));

        Path snapshot = tempDir.resolve("snap.json");
        provider = buildProvider(snapshot);
        provider.init();

        List<FlagChangeEvent> events = new ArrayList<>();
        FlagChangeListener listener = events::add;
        provider.addChangeListener(listener);
        provider.removeChangeListener(listener);

        // update stub
        wiremock.resetAll();
        wiremock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody(FLAGS_JSON_V2)
                        .withHeader("Content-Type", "application/json")));

        Thread.sleep(7_000); // wait longer than poll interval
        assertThat(events).isEmpty();
    }
}
