package com.openflags.spring;

import com.openflags.core.OpenFlagsClient;
import com.openflags.core.evaluation.EvaluationEvent;
import com.openflags.core.evaluation.EvaluationListener;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Auto-configuration tests for the {@link EvaluationListener} auto-detection.
 * <p>
 * Validates that every {@link EvaluationListener} bean in the context is wired into
 * the {@link OpenFlagsClient} builder. Order is verified by recording invocations on
 * each listener instance — no global static state is used to avoid cross-test
 * pollution.
 * </p>
 */
class EvaluationListenersAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
            .withPropertyValues(
                    "openflags.provider=file",
                    "openflags.file.path=classpath:flags-test.yml",
                    "openflags.file.watch-enabled=false");

    @Test
    void registersAllListeners_inOrder() {
        runner.withUserConfiguration(TwoListenersConfig.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(OpenFlagsClient.class);
            OpenFlagsClient client = ctx.getBean(OpenFlagsClient.class);
            RecordingListener first = ctx.getBean("firstListener", RecordingListener.class);
            RecordingListener second = ctx.getBean("secondListener", RecordingListener.class);

            client.getBooleanValue("test-feature", false);

            // Both listeners must observe the same evaluation.
            assertThat(first.events).hasSize(1);
            assertThat(second.events).hasSize(1);
            assertThat(first.events.get(0).flagKey()).isEqualTo("test-feature");
            assertThat(second.events.get(0).flagKey()).isEqualTo("test-feature");
            // Order is enforced by ObjectProvider.orderedStream() honoring @Order.
            // The dispatcher invokes listeners single-threaded, so monotonic invocation
            // counters are sufficient evidence of order.
            assertThat(first.invocationOrder).isPositive();
            assertThat(second.invocationOrder).isPositive();
            assertThat(first.invocationOrder).isLessThan(second.invocationOrder);
        });
    }

    @Test
    void clientWorks_withoutListeners() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(OpenFlagsClient.class);
            OpenFlagsClient client = ctx.getBean(OpenFlagsClient.class);
            // The empty-listeners path must not throw regardless of fixture content.
            assertThatCode(() -> client.getBooleanValue("test-feature", false))
                    .doesNotThrowAnyException();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoListenersConfig {

        private final InvocationCounter counter = new InvocationCounter();

        @Bean
        @Order(1)
        RecordingListener firstListener() {
            return new RecordingListener(counter);
        }

        @Bean
        @Order(2)
        RecordingListener secondListener() {
            return new RecordingListener(counter);
        }
    }

    /**
     * Test-only listener that records every event it observes plus its monotonic
     * invocation index inside the test context. Each context owns its own counter
     * so there is no cross-test state.
     */
    static class RecordingListener implements EvaluationListener {
        final List<EvaluationEvent> events = new CopyOnWriteArrayList<>();
        final InvocationCounter counter;
        volatile int invocationOrder = -1;

        RecordingListener(InvocationCounter counter) {
            this.counter = counter;
        }

        @Override
        public void onEvaluation(EvaluationEvent event) {
            events.add(event);
            invocationOrder = counter.next();
        }
    }

    /** Per-context monotonic counter used to derive listener invocation order. */
    static final class InvocationCounter {
        private int value = 0;

        synchronized int next() {
            return ++value;
        }
    }
}
