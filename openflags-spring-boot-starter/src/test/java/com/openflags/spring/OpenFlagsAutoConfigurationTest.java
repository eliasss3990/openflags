package com.openflags.spring;

import com.openflags.core.OpenFlagsClient;
import com.openflags.core.provider.FlagProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.*;

class OpenFlagsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
            .withPropertyValues(
                    "openflags.provider=file",
                    "openflags.file.path=classpath:flags-test.yml",
                    "openflags.file.watch-enabled=false");

    @Test
    void autoConfigures_openFlagsClient() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(OpenFlagsClient.class);
            assertThat(ctx).hasSingleBean(FlagProvider.class);
        });
    }

    @Test
    void client_evaluatesFlags() {
        contextRunner.run(ctx -> {
            OpenFlagsClient client = ctx.getBean(OpenFlagsClient.class);
            assertThat(client.getBooleanValue("test-feature", false)).isTrue();
            assertThat(client.getStringValue("test-string", "")).isEqualTo("hello");
        });
    }

    @Test
    void backoff_whenCustomClientDefined() {
        contextRunner
                .withUserConfiguration(CustomClientConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(OpenFlagsClient.class);
                    assertThat(ctx.getBean(OpenFlagsClient.class))
                            .isSameAs(ctx.getBean("customClient"));
                });
    }

    @Test
    void missingFlagFile_causesContextFailure() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
                .withPropertyValues(
                        "openflags.file.path=classpath:does-not-exist.yml",
                        "openflags.file.watch-enabled=false")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void healthIndicator_presentWhenActuatorOnClasspath() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(OpenFlagsHealthIndicator.class);
        });
    }

    @Test
    void healthIndicator_createdWithCorrectClient() {
        contextRunner.run(ctx -> {
            OpenFlagsHealthIndicator indicator = ctx.getBean(OpenFlagsHealthIndicator.class);
            assertThat(indicator).isNotNull();
            assertThat(indicator.health()).isNotNull();
        });
    }

    @Test
    void invalidProvider_failsStartupWithActionableMessage() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
                .withPropertyValues("openflags.provider=foo")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx).getFailure()
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("openflags.provider='foo'")
                            .hasMessageContaining("file")
                            .hasMessageContaining("remote")
                            .hasMessageContaining("hybrid");
                });
    }

    @Test
    void blankProvider_failsStartupWithActionableMessage() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
                .withPropertyValues("openflags.provider= ")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx).getFailure()
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("openflags.provider");
                });
    }

    @Test
    void uppercaseProvider_failsStartup_caseSensitive() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
                .withPropertyValues("openflags.provider=FILE")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void healthIndicator_absentWhenActuatorMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(HealthIndicator.class))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(OpenFlagsClient.class);
                    assertThat(ctx).doesNotHaveBean(OpenFlagsHealthIndicator.class);
                });
    }

    @org.springframework.context.annotation.Configuration
    static class CustomClientConfig {
        @org.springframework.context.annotation.Bean("customClient")
        OpenFlagsClient customClient(FlagProvider provider) {
            return OpenFlagsClient.builder().provider(provider).build();
        }
    }
}
