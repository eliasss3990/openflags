package com.openflags.core;

import com.openflags.core.evaluation.EvaluationEvent;
import com.openflags.core.evaluation.EvaluationListener;
import com.openflags.core.metrics.MetricsRecorder;
import com.openflags.core.metrics.Tag;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import com.openflags.core.provider.FlagProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Pins the contract documented on {@link EvaluationListener}: RuntimeException
 * is swallowed and reported through metrics, while {@link Error} propagates
 * and aborts the current evaluation.
 */
@ExtendWith(MockitoExtension.class)
class EvaluationDispatchErrorContractTest {

    @Mock
    private FlagProvider provider;

    private OpenFlagsClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
    }

    private void mockBoolean(String key, boolean value) {
        Flag flag = new Flag(key, FlagType.BOOLEAN, FlagValue.of(value, FlagType.BOOLEAN), true, null);
        when(provider.getFlag(key)).thenReturn(Optional.of(flag));
    }

    @Test
    void errorThrownByListener_propagatesAndAbortsEvaluation() {
        doNothing().when(provider).init();
        AtomicBoolean secondInvoked = new AtomicBoolean();
        EvaluationListener bombing = e -> {
            throw new StackOverflowError("simulated");
        };
        EvaluationListener afterBomb = e -> secondInvoked.set(true);

        client = OpenFlagsClient.builder()
                .provider(provider)
                .providerType("file")
                .addEvaluationListener(bombing)
                .addEvaluationListener(afterBomb)
                .build();
        mockBoolean("k", true);

        assertThatThrownBy(() -> client.getBooleanValue("k", false))
                .isInstanceOf(StackOverflowError.class);
        assertThat(secondInvoked).as("dispatch must abort on Error").isFalse();
    }

    @Test
    void metricsRecorderThrowingRuntimeException_doesNotBreakEvaluation() {
        doNothing().when(provider).init();
        MetricsRecorder bad = new MetricsRecorder() {
            @Override
            public void recordEvaluation(EvaluationEvent event) {
                throw new IllegalStateException("metrics down");
            }

            @Override
            public void recordPoll(String outcome, long durationNanos) {
            }

            @Override
            public void recordSnapshotWrite(String outcome, long durationNanos) {
            }

            @Override
            public void recordFlagChange(com.openflags.core.event.ChangeType type) {
            }

            @Override
            public void recordHybridFallback(String from, String to) {
            }

            @Override
            public void recordListenerError(String listenerSimpleName) {
            }

            @Override
            public void registerGauge(String name, Iterable<Tag> tags, Supplier<Number> supplier) {
            }
        };

        client = OpenFlagsClient.builder()
                .provider(provider)
                .providerType("file")
                .metricsRecorder(bad)
                .build();
        mockBoolean("k", true);

        assertThat(client.getBooleanValue("k", false)).isTrue();
    }
}
