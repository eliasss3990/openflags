package com.openflags.spring;

import com.openflags.core.OpenFlagsClient;
import com.openflags.core.OpenFlagsClientCustomizer;
import com.openflags.core.metrics.MetricsRecorder;
import com.openflags.core.metrics.MicrometerMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auto-configuration tests for the Micrometer integration.
 * <p>
 * Verifies the contract documented in the phase 5 plan: the customizer is
 * registered
 * only when {@link MeterRegistry} is on the classpath, a registry bean is
 * present and
 * {@code openflags.metrics.enabled} is not explicitly disabled. Common-tags
 * {@link MeterFilter} bean follows the same conditions.
 * </p>
 */
class MicrometerAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
            .withPropertyValues(
                    "openflags.provider=file",
                    "openflags.file.path=classpath:flags-test.yml",
                    "openflags.file.watch-enabled=false");

    @Test
    void registersCustomizer_whenMeterRegistryBeanPresent() {
        runner.withUserConfiguration(MeterRegistryConfig.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(OpenFlagsClient.class);
            assertThat(ctx).hasBean("openflagsMicrometerCustomizer");
            assertThat(ctx.getBeansOfType(OpenFlagsClientCustomizer.class))
                    .containsKey("openflagsMicrometerCustomizer");
        });
    }

    @Test
    void registersMeterFilter_whenMeterRegistryBeanPresent() {
        runner.withUserConfiguration(MeterRegistryConfig.class).run(ctx -> {
            assertThat(ctx).hasBean("openflagsCommonTagsFilter");
            assertThat(ctx.getBean("openflagsCommonTagsFilter")).isInstanceOf(MeterFilter.class);
        });
    }

    @Test
    void doesNotRegisterCustomizer_whenMetricsDisabled() {
        runner.withUserConfiguration(MeterRegistryConfig.class)
                .withPropertyValues("openflags.metrics.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(OpenFlagsClient.class);
                    assertThat(ctx).doesNotHaveBean("openflagsMicrometerCustomizer");
                    assertThat(ctx).doesNotHaveBean("openflagsCommonTagsFilter");
                });
    }

    @Test
    void doesNotRegisterCustomizer_whenNoMeterRegistryBean() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(OpenFlagsClient.class);
            assertThat(ctx).doesNotHaveBean("openflagsMicrometerCustomizer");
            assertThat(ctx).doesNotHaveBean("openflagsCommonTagsFilter");
        });
    }

    @Test
    void doesNotRegisterBindings_whenMicrometerAbsentFromClasspath() {
        runner.withUserConfiguration(MeterRegistryConfig.class)
                .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(OpenFlagsClient.class);
                    assertThat(ctx).doesNotHaveBean("openflagsMicrometerCustomizer");
                    assertThat(ctx).doesNotHaveBean("openflagsCommonTagsFilter");
                });
    }

    @Test
    void appliesStaticTags_viaMeterFilter() {
        runner.withUserConfiguration(MeterRegistryConfig.class)
                .withPropertyValues(
                        "openflags.metrics.tags.env=prod",
                        "openflags.metrics.tags.region=eu-west-1")
                .run(ctx -> {
                    MeterRegistry registry = ctx.getBean(MeterRegistry.class);
                    MeterFilter filter = (MeterFilter) ctx.getBean("openflagsCommonTagsFilter");
                    // Filter must be configured BEFORE the meter is registered;
                    // Micrometer applies common-tag filters at meter creation time only.
                    registry.config().meterFilter(filter);
                    registry.counter("openflags.test").increment();
                    assertThat(registry.find("openflags.test").counter())
                            .isNotNull()
                            .satisfies(counter -> {
                                assertThat(counter.getId().getTag("env")).isEqualTo("prod");
                                assertThat(counter.getId().getTag("region")).isEqualTo("eu-west-1");
                            });
                });
    }

    @Test
    void registersMetricsRecorderBean_whenMeterRegistryBeanPresent() {
        runner.withUserConfiguration(MeterRegistryConfig.class).run(ctx -> {
            assertThat(ctx).hasBean("openflagsMetricsRecorder");
            assertThat(ctx.getBean(MetricsRecorder.class))
                    .isInstanceOf(MicrometerMetricsRecorder.class);
        });
    }

    @Test
    void doesNotRegisterMetricsRecorderBean_whenMetricsDisabled() {
        runner.withUserConfiguration(MeterRegistryConfig.class)
                .withPropertyValues("openflags.metrics.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean("openflagsMetricsRecorder"));
    }

    @Test
    void doesNotRegisterMetricsRecorderBean_whenNoMeterRegistryBean() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean("openflagsMetricsRecorder"));
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
