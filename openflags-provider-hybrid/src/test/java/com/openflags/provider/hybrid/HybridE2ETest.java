package com.openflags.provider.hybrid;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.openflags.core.OpenFlagsClient;
import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.provider.ProviderState;
import com.openflags.provider.remote.RemoteProviderConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test for HybridFlagProvider with multivariant flags and OpenFlagsClient.
 */
class HybridE2ETest {

    private static final String MULTIVARIANT_FLAGS_JSON = """
            {
              "flags": {
                "ab-experiment": {
                  "type": "string",
                  "value": "control",
                  "enabled": true,
                  "rules": [
                    {
                      "name": "ab-split",
                      "kind": "multivariant",
                      "variants": [
                        { "value": "control", "weight": 50 },
                        { "value": "treatment", "weight": 50 }
                      ]
                    }
                  ]
                },
                "simple-bool": {
                  "type": "boolean",
                  "value": true,
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
    void setup() {
        wiremock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wiremock.start();
        wiremock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody(MULTIVARIANT_FLAGS_JSON)
                        .withHeader("Content-Type", "application/json")));
    }

    @AfterEach
    void teardown() {
        if (provider != null) {
            try {
                provider.shutdown();
            } catch (Exception ignored) {}
        }
        wiremock.stop();
    }

    @Test
    void multivariantDistribution_via_hybrid() throws Exception {
        Path snapshot = tempDir.resolve("snap.json");
        provider = HybridFlagProvider.builder()
                .remoteConfig(new RemoteProviderConfig(
                        URI.create("http://localhost:" + wiremock.port()),
                        "/flags", null, null,
                        Duration.ofSeconds(2), Duration.ofSeconds(5),
                        Duration.ofSeconds(5), Duration.ofSeconds(30),
                        "test-e2e"))
                .snapshotPath(snapshot)
                .snapshotFormat(SnapshotFormat.JSON)
                .watchSnapshot(false)
                .snapshotDebounce(Duration.ofMillis(200))
                .build();
        provider.init();

        assertThat(provider.getState()).isEqualTo(ProviderState.READY);

        OpenFlagsClient client = OpenFlagsClient.builder().provider(provider).build();

        // distribute 10k user keys across the multivariant rule
        Map<String, Integer> distribution = new HashMap<>();
        int total = 10_000;
        for (int i = 0; i < total; i++) {
            String userId = "user-" + i;
            EvaluationContext ctx = EvaluationContext.builder().targetingKey(userId).build();
            String variant = client.getStringValue("ab-experiment", "control", ctx);
            distribution.merge(variant, 1, Integer::sum);
        }

        // expect roughly 50/50 split (allow ±5%)
        int controlCount = distribution.getOrDefault("control", 0);
        int treatmentCount = distribution.getOrDefault("treatment", 0);
        assertThat(controlCount + treatmentCount).isEqualTo(total);
        assertThat(controlCount).isBetween(4500, 5500);
        assertThat(treatmentCount).isBetween(4500, 5500);

        // verify snapshot was written and contains multivariant rules
        Awaitility.await().atMost(3, TimeUnit.SECONDS)
                .until(() -> Files.exists(snapshot) && Files.size(snapshot) > 0);

        String snapshotContent = Files.readString(snapshot);
        assertThat(snapshotContent).contains("ab-experiment");
        assertThat(snapshotContent).contains("multivariant");
        assertThat(snapshotContent).contains("control");
        assertThat(snapshotContent).contains("treatment");
    }

    @Test
    void fallback_servesSnapshotData_when_remoteDown() throws Exception {
        Path snapshot = tempDir.resolve("snap.json");
        provider = HybridFlagProvider.builder()
                .remoteConfig(new RemoteProviderConfig(
                        URI.create("http://localhost:" + wiremock.port()),
                        "/flags", null, null,
                        Duration.ofSeconds(2), Duration.ofSeconds(5),
                        Duration.ofSeconds(5), Duration.ofSeconds(30),
                        "test-e2e-fallback"))
                .snapshotPath(snapshot)
                .snapshotFormat(SnapshotFormat.JSON)
                .watchSnapshot(false)
                .snapshotDebounce(Duration.ofMillis(200))
                .build();
        provider.init();

        // wait for snapshot to be written
        Awaitility.await().atMost(3, TimeUnit.SECONDS)
                .until(() -> Files.exists(snapshot) && Files.size(snapshot) > 0);

        // shut down current provider and create a new one using the snapshot
        provider.shutdown();

        // Now simulate cold start with backend down but snapshot exists
        provider = HybridFlagProvider.builder()
                .remoteConfig(new RemoteProviderConfig(
                        URI.create("http://localhost:19996"),
                        "/flags", null, null,
                        Duration.ofMillis(500), Duration.ofMillis(1000),
                        Duration.ofSeconds(5), Duration.ofSeconds(30),
                        "test-e2e-fallback"))
                .snapshotPath(snapshot)
                .snapshotFormat(SnapshotFormat.JSON)
                .watchSnapshot(false)
                .snapshotDebounce(Duration.ofMillis(200))
                .build();

        provider.init(); // remote fails, file succeeds

        OpenFlagsClient client = OpenFlagsClient.builder().provider(provider).build();
        assertThat(client.getBooleanValue("simple-bool", false)).isTrue();
    }
}
