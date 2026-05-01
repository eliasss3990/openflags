package com.openflags.core;

import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationListener;
import com.openflags.core.evaluation.FlagEvaluator;
import com.openflags.core.metrics.MetricsRecorder;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import com.openflags.core.provider.FlagProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenFlagsClientMdcTest {

    @Mock
    private FlagProvider provider;

    private OpenFlagsClient client;

    @BeforeEach
    void setUp() {
        doNothing().when(provider).init();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        if (client != null)
            client.shutdown();
        MDC.clear();
    }

    private OpenFlagsClient buildClient(boolean auditMdc, EvaluationListener... ls) {
        OpenFlagsClientBuilder b = OpenFlagsClient.builder()
                .provider(provider)
                .providerType("file")
                .auditMdcEnabled(auditMdc);
        for (EvaluationListener l : ls)
            b.addEvaluationListener(l);
        return b.build();
    }

    private void mockBoolean(String key) {
        Flag f = new Flag(key, FlagType.BOOLEAN, FlagValue.of(true, FlagType.BOOLEAN), true, null);
        when(provider.getFlag(key)).thenReturn(Optional.of(f));
    }

    @Test
    void mdcDisabled_byDefault_keysNotSet() {
        String[] capturedFlag = new String[1];
        client = buildClient(false, e -> capturedFlag[0] = MDC.get("openflags.flag_key"));
        mockBoolean("k");

        client.getBooleanValue("k", false);

        assertThat(capturedFlag[0]).isNull();
        assertThat(MDC.get("openflags.flag_key")).isNull();
    }

    @Test
    void mdcEnabled_setsKeysDuringEvaluation_andClearsAfter() {
        String[] capturedFlag = new String[1];
        String[] capturedTk = new String[1];
        client = buildClient(true, e -> {
            capturedFlag[0] = MDC.get("openflags.flag_key");
            capturedTk[0] = MDC.get("openflags.targeting_key");
        });
        mockBoolean("k");

        client.getBooleanValue("k", false, EvaluationContext.of("user-1"));

        assertThat(capturedFlag[0]).isEqualTo("k");
        assertThat(capturedTk[0]).isEqualTo("user-1");
        assertThat(MDC.get("openflags.flag_key")).isNull();
        assertThat(MDC.get("openflags.targeting_key")).isNull();
    }

    @Test
    void mdcEnabled_withoutTargetingKey_doesNotInheritPreExisting() {
        MDC.put("openflags.targeting_key", "stale");
        String[] captured = new String[1];
        client = buildClient(true, e -> captured[0] = MDC.get("openflags.targeting_key"));
        mockBoolean("k");

        client.getBooleanValue("k", false);

        assertThat(captured[0]).isNull();
        assertThat(MDC.get("openflags.targeting_key")).isEqualTo("stale");
    }

    @Test
    void mdcEnabled_doesNotTouchConsumerKeys() {
        MDC.put("traceId", "abc-123");
        client = buildClient(true);
        mockBoolean("k");

        client.getBooleanValue("k", false, EvaluationContext.of("u"));

        assertThat(MDC.get("traceId")).isEqualTo("abc-123");
    }

    @Test
    void nestedEvaluation_restoresOuterMdc() {
        String[] outerAfter = new String[1];
        EvaluationListener listener = e -> {
            if (e.flagKey().equals("outer")) {
                client.getBooleanValue("inner", false, EvaluationContext.of("u-inner"));
                outerAfter[0] = MDC.get("openflags.flag_key") + "/" + MDC.get("openflags.targeting_key");
            }
        };
        client = buildClient(true, listener);
        mockBoolean("outer");
        mockBoolean("inner");

        client.getBooleanValue("outer", false, EvaluationContext.of("u-outer"));

        assertThat(outerAfter[0]).isEqualTo("outer/u-outer");
        assertThat(MDC.get("openflags.flag_key")).isNull();
    }

    @Test
    void exceptionInEvaluator_stillRestoresMdc() {
        when(provider.getFlag("k")).thenThrow(new RuntimeException("boom"));
        client = buildClient(true);
        MDC.put("openflags.flag_key", "previous");

        try {
            client.getBooleanValue("k", false);
        } catch (RuntimeException ignored) {
            // evaluator may absorb or rethrow; we only care about MDC state below
        }

        assertThat(MDC.get("openflags.flag_key")).isEqualTo("previous");
    }
}
