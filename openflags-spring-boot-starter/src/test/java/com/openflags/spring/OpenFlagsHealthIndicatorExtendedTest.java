package com.openflags.spring;

import com.openflags.core.model.Flag;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderDiagnostics;
import com.openflags.core.provider.ProviderState;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the extended {@link OpenFlagsHealthIndicator} that surfaces
 * {@link ProviderDiagnostics} details when the active {@link FlagProvider}
 * implements
 * the optional contract.
 */
class OpenFlagsHealthIndicatorExtendedTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class));

    @Test
    void filePath_includesDiagnosticsDetails() {
        runner.withPropertyValues(
                "openflags.provider=file",
                "openflags.file.path=classpath:flags-test.yml",
                "openflags.file.watch-enabled=false")
                .run(ctx -> {
                    OpenFlagsHealthIndicator indicator = ctx.getBean(OpenFlagsHealthIndicator.class);
                    Health health = indicator.health();
                    assertThat(health.getStatus()).isEqualTo(Status.UP);
                    Map<String, Object> details = health.getDetails();
                    assertThat(details).containsEntry("provider.state", ProviderState.READY);
                    assertThat(details).containsEntry("provider.type", "file");
                    assertThat(details).containsKey("file.path");
                    assertThat(details).containsKey("file.flag_count");
                });
    }

    @Test
    void providerWithoutDiagnostics_reportsBasicState() {
        runner.withUserConfiguration(MockProviderConfig.class).run(ctx -> {
            OpenFlagsHealthIndicator indicator = ctx.getBean(OpenFlagsHealthIndicator.class);
            Health health = indicator.health();
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsOnlyKeys("provider.state");
        });
    }

    @Test
    void providerWithDiagnostics_addsTypeAndDiagnosticsMap() {
        runner.withUserConfiguration(MockDiagnosticsProviderConfig.class).run(ctx -> {
            OpenFlagsHealthIndicator indicator = ctx.getBean(OpenFlagsHealthIndicator.class);
            Health health = indicator.health();
            assertThat(health.getDetails())
                    .containsEntry("provider.type", "fake")
                    .containsEntry("fake.detail", "value")
                    .containsEntry("provider.state", ProviderState.READY);
        });
    }

    @Test
    void providerInDegradedState_reportsOutOfService() {
        runner.withUserConfiguration(DegradedProviderConfig.class).run(ctx -> {
            OpenFlagsHealthIndicator indicator = ctx.getBean(OpenFlagsHealthIndicator.class);
            Health health = indicator.health();
            assertThat(health.getStatus().getCode()).isEqualTo(Status.OUT_OF_SERVICE.getCode());
            assertThat(health.getDetails()).containsEntry("provider.state", ProviderState.DEGRADED);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class MockProviderConfig {
        @Bean
        FlagProvider flagProvider() {
            FlagProvider provider = mock(FlagProvider.class);
            when(provider.getState()).thenReturn(ProviderState.READY);
            return provider;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MockDiagnosticsProviderConfig {
        @Bean
        FlagProvider flagProvider() {
            return new FakeDiagnosticsProvider(ProviderState.READY);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DegradedProviderConfig {
        @Bean
        FlagProvider flagProvider() {
            FlagProvider provider = mock(FlagProvider.class);
            when(provider.getState()).thenReturn(ProviderState.DEGRADED);
            return provider;
        }
    }

    /**
     * Test double that exposes both {@link FlagProvider} and
     * {@link ProviderDiagnostics}.
     */
    static final class FakeDiagnosticsProvider implements FlagProvider, ProviderDiagnostics {
        private final ProviderState state;

        FakeDiagnosticsProvider(ProviderState state) {
            this.state = state;
        }

        @Override
        public ProviderState getState() {
            return state;
        }

        @Override
        public String providerType() {
            return "fake";
        }

        @Override
        public Map<String, Object> diagnostics() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fake.detail", "value");
            return map;
        }

        @Override
        public Instant lastUpdate() {
            return Instant.EPOCH;
        }

        @Override
        public int flagCount() {
            return 0;
        }

        @Override
        public void init() {
        }

        @Override
        public Optional<Flag> getFlag(String key) {
            return Optional.empty();
        }

        @Override
        public Map<String, Flag> getAllFlags() {
            return Collections.emptyMap();
        }

        @Override
        public void addChangeListener(FlagChangeListener listener) {
        }

        @Override
        public void removeChangeListener(FlagChangeListener listener) {
        }

        @Override
        public void shutdown() {
        }
    }
}
