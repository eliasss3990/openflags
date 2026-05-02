package com.openflags.spring;

import com.openflags.core.OpenFlagsClient;
import com.openflags.core.evaluation.EvaluationEvent;
import com.openflags.core.evaluation.EvaluationListener;
import com.openflags.core.provider.ProviderState;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end observability test exercising the full Spring Boot integration
 * with
 * Actuator, Micrometer and a custom {@link EvaluationListener} bean.
 * <p>
 * Verifies that:
 * <ul>
 * <li>the auto-detected listener receives an event for every evaluation;</li>
 * <li>Micrometer counters are populated through the auto-configured
 * {@code openflagsMicrometerCustomizer};</li>
 * <li>the {@link OpenFlagsHealthIndicator} surfaces provider diagnostics
 * (provider type, flag count, file path) when the active provider
 * implements {@code ProviderDiagnostics}.</li>
 * </ul>
 * </p>
 * <p>
 * The hybrid/remote circuit-breaker fallout to
 * {@code openflags.hybrid.fallback.total}
 * and {@code openflags.poll.total} are covered by dedicated tests in their
 * respective
 * provider modules; this test focuses on the wiring pulled together by the
 * starter.
 * </p>
 */
@SpringBootTest(classes = ObservabilityE2ETest.E2EConfig.class)
@TestPropertySource(properties = {
        "openflags.provider=file",
        "openflags.file.path=classpath:flags-test.yml",
        "openflags.file.watch-enabled=false",
        "openflags.metrics.enabled=true",
        "openflags.metrics.tag-flag-key=true"
})
class ObservabilityE2ETest {

    @Autowired
    OpenFlagsClient client;

    @Autowired
    MeterRegistry registry;

    @Autowired
    OpenFlagsHealthIndicator healthIndicator;

    @Autowired
    CountingListener listener;

    @Test
    void evaluations_propagateToListenerCountersAndHealth() {
        int evaluations = 100;
        for (int i = 0; i < evaluations; i++) {
            assertThat(client.getBooleanValue("test-feature", false)).isTrue();
        }

        assertThat(listener.events()).hasSize(evaluations);
        assertThat(listener.events()).allSatisfy(event -> assertThat(event.flagKey()).isEqualTo("test-feature"));

        assertThat(registry.find("openflags.evaluations.total")
                .tag("flag", "test-feature")
                .counter())
                .as("evaluation counter populated by MicrometerMetricsRecorder")
                .isNotNull()
                .satisfies(c -> assertThat(c.count()).isEqualTo((double) evaluations));

        Health health = healthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("provider.state", ProviderState.READY)
                .containsEntry("provider.type", "file")
                .containsKey("file.path")
                .containsKey("file.flag_count");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class E2EConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        CountingListener countingListener() {
            return new CountingListener();
        }
    }

    static final class CountingListener implements EvaluationListener {
        private final List<EvaluationEvent> received = new CopyOnWriteArrayList<>();

        @Override
        public void onEvaluation(EvaluationEvent event) {
            received.add(event);
        }

        List<EvaluationEvent> events() {
            return received;
        }
    }
}
