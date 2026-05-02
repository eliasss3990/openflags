package com.openflags.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binding tests for the observability-related properties added in phase 5:
 * {@code openflags.metrics.*}, {@code openflags.audit.*} and the new circuit breaker
 * fields under {@code openflags.remote.*}.
 */
@SpringBootTest(classes = OpenFlagsPropertiesObservabilityTest.Config.class)
@TestPropertySource(properties = {
        "openflags.metrics.enabled=false",
        "openflags.metrics.tag-flag-key=false",
        "openflags.metrics.tags.env=prod",
        "openflags.metrics.tags.region=eu-west-1",
        "openflags.audit.mdc-enabled=true",
        "openflags.remote.base-url=https://flags.example.com",
        "openflags.remote.failure-threshold=10",
        "openflags.remote.max-backoff=2m"
})
class OpenFlagsPropertiesObservabilityTest {

    @EnableConfigurationProperties(OpenFlagsProperties.class)
    static class Config {
    }

    @Autowired
    private OpenFlagsProperties properties;

    @Test
    void bindsMetricsEnabled() {
        assertThat(properties.getMetrics().isEnabled()).isFalse();
    }

    @Test
    void bindsMetricsTagFlagKey() {
        assertThat(properties.getMetrics().isTagFlagKey()).isFalse();
    }

    @Test
    void bindsMetricsTags() {
        assertThat(properties.getMetrics().getTags())
                .containsEntry("env", "prod")
                .containsEntry("region", "eu-west-1");
    }

    @Test
    void bindsAuditMdcEnabled() {
        assertThat(properties.getAudit().isMdcEnabled()).isTrue();
    }

    @Test
    void bindsRemoteFailureThreshold() {
        assertThat(properties.getRemote().getFailureThreshold()).isEqualTo(10);
    }

    @Test
    void bindsRemoteMaxBackoff() {
        assertThat(properties.getRemote().getMaxBackoff()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void defaults_whenNotConfigured() {
        OpenFlagsProperties defaults = new OpenFlagsProperties();
        assertThat(defaults.getMetrics().isEnabled()).isTrue();
        assertThat(defaults.getMetrics().isTagFlagKey()).isTrue();
        assertThat(defaults.getMetrics().getTags()).isEmpty();
        assertThat(defaults.getAudit().isMdcEnabled()).isFalse();
        assertThat(defaults.getRemote().getFailureThreshold()).isEqualTo(5);
        assertThat(defaults.getRemote().getMaxBackoff()).isEqualTo(Duration.ofMinutes(5));
    }
}
