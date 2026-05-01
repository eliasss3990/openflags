package com.openflags.provider.remote;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.openflags.core.provider.ProviderState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RemoteFlagProviderCircuitBreakerTest {

    private static final String FLAGS_JSON = """
            { "flags": { "x": { "type": "boolean", "value": true, "enabled": true } } }
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

    @Test
    void diagnosticsExposeCircuitBreakerState() {
        wireMock.stubFor(get(urlEqualTo("/flags")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(FLAGS_JSON)));

        RemoteFlagProvider p = RemoteFlagProviderBuilder
                .forUrl(URI.create("http://localhost:" + wireMock.port()))
                .pollInterval(Duration.ofSeconds(5))
                .cacheTtl(Duration.ofMinutes(1))
                .failureThreshold(3)
                .maxBackoff(Duration.ofMinutes(2))
                .build();
        p.init();

        Map<String, Object> diagnostics = p.diagnostics();
        assertThat(diagnostics)
                .containsEntry("remote.consecutive_failures", 0)
                .containsEntry("remote.circuit_open", false)
                .containsEntry("remote.flag_count", 1)
                .containsEntry("remote.state", "READY")
                .containsKey("remote.last_fetch")
                .containsKey("remote.next_poll_in_ms");

        assertThat(p.providerType()).isEqualTo("remote");
        assertThat(p.flagCount()).isEqualTo(1);
        assertThat(p.lastUpdate()).isNotNull();

        p.shutdown();
    }

    @Test
    void onPollOutcome_invokedWithSuccessAndFailureTags() throws Exception {
        // Stub: first call 200, subsequent calls 500.
        wireMock.stubFor(get(urlEqualTo("/flags")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(FLAGS_JSON)));

        RemoteFlagProvider p = RemoteFlagProviderBuilder
                .forUrl(URI.create("http://localhost:" + wireMock.port()))
                .pollInterval(Duration.ofSeconds(5))
                .cacheTtl(Duration.ofMinutes(1))
                .failureThreshold(2)
                .maxBackoff(Duration.ofSeconds(10))
                .build();

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        p.setPollListener(new RemotePollListener() {
            @Override
            public void onPollComplete(Map<String, com.openflags.core.model.Flag> snapshot) {
                // ignored
            }

            @Override
            public void onPollOutcome(String outcome, long durationNanos) {
                if (outcome.equals("success")) successes.incrementAndGet();
                else failures.incrementAndGet();
                assertThat(durationNanos).isGreaterThanOrEqualTo(0);
            }
        });

        p.init();

        // Replace stub: now return 500 for following polls.
        wireMock.resetMappings();
        wireMock.stubFor(get(urlEqualTo("/flags")).willReturn(aResponse().withStatus(500)));

        await().atMost(Duration.ofSeconds(20)).until(() ->
                (Boolean) p.diagnostics().get("remote.circuit_open"));

        Map<String, Object> diag = p.diagnostics();
        assertThat(failures.get()).isGreaterThanOrEqualTo(1);
        assertThat((Integer) diag.get("remote.consecutive_failures")).isGreaterThanOrEqualTo(2);
        assertThat(p.getState()).isIn(ProviderState.DEGRADED, ProviderState.ERROR);

        p.shutdown();
    }

    @Test
    void metricsRecordingPollListener_forwardsOutcomesToRecorder() {
        wireMock.stubFor(get(urlEqualTo("/flags")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(FLAGS_JSON)));

        RecordingMetrics metrics = new RecordingMetrics();
        RemoteFlagProvider p = RemoteFlagProviderBuilder
                .forUrl(URI.create("http://localhost:" + wireMock.port()))
                .pollInterval(Duration.ofSeconds(5))
                .cacheTtl(Duration.ofMinutes(1))
                .build();
        p.setPollListener(new MetricsRecordingPollListener(metrics));

        p.init();
        await().atMost(Duration.ofSeconds(15)).until(() -> metrics.successCount.get() >= 1);

        assertThat(metrics.successCount.get()).isGreaterThanOrEqualTo(1);
        p.shutdown();
    }

    private static final class RecordingMetrics implements com.openflags.core.metrics.MetricsRecorder {
        final AtomicInteger successCount = new AtomicInteger();
        final AtomicInteger failureCount = new AtomicInteger();

        @Override public void recordEvaluation(com.openflags.core.evaluation.EvaluationEvent event) { }
        @Override public void recordPoll(String outcome, long durationNanos) {
            if ("success".equals(outcome)) successCount.incrementAndGet();
            else failureCount.incrementAndGet();
        }
        @Override public void recordSnapshotWrite(String outcome, long durationNanos) { }
        @Override public void recordFlagChange(com.openflags.core.event.ChangeType type) { }
        @Override public void recordHybridFallback(String from, String to) { }
        @Override public void recordListenerError(String listenerSimpleName) { }
        @Override public void registerGauge(String name, Iterable<com.openflags.core.metrics.Tag> tags,
                                            java.util.function.Supplier<Number> supplier) { }
    }
}
