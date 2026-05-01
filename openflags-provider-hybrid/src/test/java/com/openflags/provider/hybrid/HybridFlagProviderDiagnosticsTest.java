package com.openflags.provider.hybrid;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.openflags.core.evaluation.EvaluationEvent;
import com.openflags.core.event.ChangeType;
import com.openflags.core.metrics.MetricsRecorder;
import com.openflags.core.metrics.Tag;
import com.openflags.provider.remote.RemoteProviderConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class HybridFlagProviderDiagnosticsTest {

    private static final String FLAGS_JSON = """
            { "flags": { "x": { "type": "boolean", "value": true, "enabled": true } } }
            """;

    private WireMockServer wiremock;
    private HybridFlagProvider provider;

    @TempDir
    Path tempDir;

    @BeforeEach
    void start() {
        wiremock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wiremock.start();
    }

    @AfterEach
    void stop() {
        if (provider != null) {
            try {
                provider.shutdown();
            } catch (Exception ignored) {
            }
        }
        wiremock.stop();
    }

    private RemoteProviderConfig remoteConfig() {
        return new RemoteProviderConfig(
                URI.create("http://localhost:" + wiremock.port()),
                "/flags", null, null,
                Duration.ofSeconds(2), Duration.ofSeconds(5),
                Duration.ofSeconds(5), Duration.ofSeconds(30),
                "test-hybrid");
    }

    private HybridFlagProvider build(Path snapshot) {
        return HybridFlagProvider.builder()
                .remoteConfig(remoteConfig())
                .snapshotPath(snapshot)
                .snapshotFormat(SnapshotFormat.JSON)
                .watchSnapshot(false)
                .snapshotDebounce(Duration.ofMillis(200))
                .build();
    }

    @Test
    void diagnostics_includesHybridRemoteAndFileKeys() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/flags")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(FLAGS_JSON)));

        Path snapshot = tempDir.resolve("snap.json");
        provider = build(snapshot);
        provider.init();

        Awaitility.await().atMost(3, TimeUnit.SECONDS)
                .until(() -> java.nio.file.Files.exists(snapshot));

        Map<String, Object> diag = provider.diagnostics();
        assertThat(diag)
                .containsKey("hybrid.routing_target")
                .containsKey("hybrid.snapshot_path")
                .containsKey("hybrid.snapshot_age_seconds")
                .containsKey("hybrid.last_snapshot_write")
                .containsKey("remote.base_url")
                .containsKey("remote.flag_count")
                .containsKey("file.path")
                .containsKey("file.flag_count");

        assertThat(provider.providerType()).isEqualTo("hybrid");
        assertThat(provider.flagCount()).isGreaterThanOrEqualTo(1);
        assertThat(provider.lastUpdate()).isNotNull();
    }

    @Test
    void routingTransition_invokesRecordHybridFallback() {
        // Backend down so init() falls through to file-only mode (file init also
        // fails);
        // failIfNoFallback defaults to false → init succeeds.
        wiremock.stubFor(get(urlEqualTo("/flags")).willReturn(aResponse().withStatus(500)));

        Path snapshot = tempDir.resolve("snap.json");
        provider = build(snapshot);

        RecordingMetrics metrics = new RecordingMetrics();
        provider.setMetricsRecorder(metrics);
        provider.init();

        // First read: remote is ERROR → routing flips remote → file
        try {
            provider.getFlag("x");
        } catch (Exception ignored) {
        }

        assertThat(metrics.fallbacks)
                .as("expected at least one routing transition")
                .anyMatch(p -> p.from.equals("remote") && p.to.equals("file"));
    }

    @Test
    void diagnostics_snapshotAgeMinusOneBeforeFirstWrite() {
        wiremock.stubFor(get(urlEqualTo("/flags")).willReturn(aResponse().withStatus(500)));
        Path snapshot = tempDir.resolve("snap.json");
        provider = build(snapshot);
        provider.init();

        Map<String, Object> diag = provider.diagnostics();
        assertThat(diag.get("hybrid.snapshot_age_seconds")).isEqualTo(-1L);
        assertThat(diag.get("hybrid.last_snapshot_write")).isEqualTo("");
    }

    private static final class RecordingMetrics implements MetricsRecorder {
        record Pair(String from, String to) {
        }

        final List<Pair> fallbacks = new ArrayList<>();

        @Override
        public void recordEvaluation(EvaluationEvent event) {
        }

        @Override
        public void recordPoll(String outcome, long durationNanos) {
        }

        @Override
        public void recordSnapshotWrite(String outcome, long durationNanos) {
        }

        @Override
        public void recordFlagChange(ChangeType type) {
        }

        @Override
        public synchronized void recordHybridFallback(String from, String to) {
            fallbacks.add(new Pair(from, to));
        }

        @Override
        public void recordListenerError(String listenerSimpleName) {
        }

        @Override
        public void registerGauge(String name, Iterable<Tag> tags, Supplier<Number> supplier) {
        }
    }
}
