package com.openflags.spring;

import com.openflags.core.OpenFlagsClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fail-fast contract for {@code openflags.file.debounce}.
 * <p>
 * The validation only fires when {@code openflags.file.watch-enabled=true}; when
 * watching is disabled the debounce value is irrelevant and a misconfiguration
 * must not crash startup.
 * </p>
 */
class FileDebounceValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class));

    @Test
    void zeroDebounce_failsFast_whenWatchEnabled() {
        runner.withPropertyValues(
                "openflags.provider=file",
                "openflags.file.path=classpath:flags-test.yml",
                "openflags.file.watch-enabled=true",
                "openflags.file.debounce=0ms")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("openflags.file.debounce");
                });
    }

    @Test
    void negativeDebounce_failsFast_whenWatchEnabled() {
        runner.withPropertyValues(
                "openflags.provider=file",
                "openflags.file.path=classpath:flags-test.yml",
                "openflags.file.watch-enabled=true",
                "openflags.file.debounce=-1ms")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("openflags.file.debounce");
                });
    }

    @Test
    void invalidDebounce_isIgnored_whenWatchDisabled() {
        runner.withPropertyValues(
                "openflags.provider=file",
                "openflags.file.path=classpath:flags-test.yml",
                "openflags.file.watch-enabled=false",
                "openflags.file.debounce=0ms")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(OpenFlagsClient.class);
                });
    }

    @Test
    void validDebounce_startsSuccessfully() {
        runner.withPropertyValues(
                "openflags.provider=file",
                "openflags.file.path=classpath:flags-test.yml",
                "openflags.file.watch-enabled=true",
                "openflags.file.debounce=300ms")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(OpenFlagsClient.class);
                });
    }
}
