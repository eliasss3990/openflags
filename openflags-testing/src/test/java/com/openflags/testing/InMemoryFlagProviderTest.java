package com.openflags.testing;

import com.openflags.core.OpenFlagsClient;
import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationReason;
import com.openflags.core.evaluation.EvaluationResult;
import com.openflags.core.evaluation.rule.Condition;
import com.openflags.core.evaluation.rule.Operator;
import com.openflags.core.evaluation.rule.TargetingRule;
import com.openflags.core.event.ChangeType;
import com.openflags.core.event.FlagChangeEvent;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import com.openflags.core.provider.ProviderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class InMemoryFlagProviderTest {

    private InMemoryFlagProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InMemoryFlagProvider();
        provider.init();
    }

    @Test
    void init_isIdempotent() {
        provider.init();
        assertThat(provider.getState()).isEqualTo(ProviderState.READY);
    }

    @Test
    void setBoolean_andGet() {
        provider.setBoolean("dark-mode", true);
        assertThat(provider.getFlag("dark-mode")).isPresent();
        assertThat(provider.getFlag("dark-mode").orElseThrow().type()).isEqualTo(FlagType.BOOLEAN);
        assertThat(provider.getFlag("dark-mode").orElseThrow().value().asBoolean()).isTrue();
    }

    @Test
    void setString_andGet() {
        provider.setString("theme", "dark");
        assertThat(provider.getFlag("theme").orElseThrow().value().asString()).isEqualTo("dark");
    }

    @Test
    void setNumber_andGet() {
        provider.setNumber("rate", 0.5);
        assertThat(provider.getFlag("rate").orElseThrow().value().asNumber()).isEqualTo(0.5);
    }

    @Test
    void setObject_andGet() {
        provider.setObject("config", Map.of("timeout", 30));
        assertThat(provider.getFlag("config").orElseThrow().value().asObject()).containsEntry("timeout", 30);
    }

    @Test
    void setDisabled_makesDisabled() {
        provider.setBoolean("feature", true);
        provider.setDisabled("feature");
        assertThat(provider.getFlag("feature").orElseThrow().enabled()).isFalse();
    }

    @Test
    void setDisabled_preservesRules() {
        FlagValue defaultValue = FlagValue.of(false, FlagType.BOOLEAN);
        FlagValue ruleValue = FlagValue.of(true, FlagType.BOOLEAN);
        TargetingRule rule = new TargetingRule("ar-only",
                List.of(new Condition("country", Operator.EQ, "AR")), ruleValue);
        Flag flag = new Flag("feature-rules", FlagType.BOOLEAN, defaultValue, true, null, List.of(rule));
        provider.setFlag(flag);

        provider.setDisabled("feature-rules");

        Flag disabled = provider.getFlag("feature-rules").orElseThrow();
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.rules()).isNotEmpty();
        assertThat(disabled.rules().get(0).name()).isEqualTo("ar-only");
    }

    @Test
    void setDisabled_throwsWhenFlagNotFound() {
        assertThatThrownBy(() -> provider.setDisabled("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remove_removesFlag() {
        provider.setBoolean("flag", true);
        provider.remove("flag");
        assertThat(provider.getFlag("flag")).isEmpty();
    }

    @Test
    void clear_removesAllFlags() {
        provider.setBoolean("a", true).setString("b", "x");
        provider.clear();
        assertThat(provider.getAllFlags()).isEmpty();
    }

    @Test
    void chaining_works() {
        provider.setBoolean("a", true).setString("b", "x").setNumber("c", 1.0);
        assertThat(provider.getAllFlags()).containsKeys("a", "b", "c");
    }

    @Test
    void changeListeners_receivedOnSet() {
        List<FlagChangeEvent> events = new CopyOnWriteArrayList<>();
        provider.addChangeListener(events::add);
        provider.setBoolean("f", true);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).changeType()).isEqualTo(ChangeType.CREATED);
    }

    @Test
    void changeListeners_receivedOnUpdate() {
        provider.setBoolean("f", false);
        List<FlagChangeEvent> events = new CopyOnWriteArrayList<>();
        provider.addChangeListener(events::add);
        provider.setBoolean("f", true);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).changeType()).isEqualTo(ChangeType.ENABLED);
    }

    @Test
    void changeListeners_receivedOnRemove() {
        provider.setBoolean("f", true);
        List<FlagChangeEvent> events = new CopyOnWriteArrayList<>();
        provider.addChangeListener(events::add);
        provider.remove("f");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).changeType()).isEqualTo(ChangeType.DELETED);
    }

    @Test
    void removeChangeListener_stopsReceivingEvents() {
        List<FlagChangeEvent> events = new CopyOnWriteArrayList<>();
        FlagChangeListener listener = events::add;
        provider.addChangeListener(listener);
        provider.removeChangeListener(listener);
        provider.setBoolean("flag", true);
        assertThat(events).isEmpty();
    }

    @Test
    void removeChangeListener_withDifferentInstance_doesNothing() {
        List<FlagChangeEvent> events = new CopyOnWriteArrayList<>();
        provider.addChangeListener(events::add);
        provider.removeChangeListener(e -> {});
        provider.setBoolean("flag-neg", true);
        assertThat(events).hasSize(1);
    }

    @Test
    void getFlag_nullKey_throwsNullPointerException() {
        assertThatThrownBy(() -> provider.getFlag(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("key");
    }

    @Test
    void init_afterShutdown_throwsIllegalState() {
        provider.shutdown();
        assertThatThrownBy(() -> provider.init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shut down");
    }

    @Test
    void emit_listenerThrowingException_doesNotStopOtherListeners() {
        List<FlagChangeEvent> received = new CopyOnWriteArrayList<>();
        provider.addChangeListener(e -> { throw new RuntimeException("listener exploded"); });
        provider.addChangeListener(received::add);

        provider.setBoolean("flag", true);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).flagKey()).isEqualTo("flag");
    }

    @Test
    void shutdown_isIdempotent() {
        provider.shutdown();
        assertThatCode(provider::shutdown).doesNotThrowAnyException();
    }

    @Test
    void evaluationAfterShutdown_throwsIllegalState() {
        provider.shutdown();
        assertThatThrownBy(() -> provider.getFlag("any"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(provider::getAllFlags)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void removeAfterShutdown_throwsIllegalState() {
        provider.setBoolean("flag", true);
        provider.shutdown();
        assertThatThrownBy(() -> provider.remove("flag"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void writesAfterShutdown_throwIllegalState() {
        provider.setBoolean("existing", true);
        provider.shutdown();

        assertThatThrownBy(() -> provider.setBoolean("k", true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider.setString("k", "v"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider.setNumber("k", 1.0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider.setObject("k", java.util.Map.of("a", 1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider.setDisabled("existing"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(provider::clear)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void concurrentWrites_areThreadSafe() throws InterruptedException {
        int threadCount = 10;
        int flagsPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < flagsPerThread; i++) {
                        provider.setBoolean("flag-" + threadId + "-" + i, true);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(provider.getAllFlags()).hasSize(threadCount * flagsPerThread);
    }

    // Phase 2: flags with rules

    @Test
    void flagWithRules_evaluatesTargetingRuleCorrectly() {
        FlagValue defaultValue = FlagValue.of(false, FlagType.BOOLEAN);
        FlagValue ruleValue = FlagValue.of(true, FlagType.BOOLEAN);
        TargetingRule rule = new TargetingRule("ar-only",
                List.of(new Condition("country", Operator.EQ, "AR")), ruleValue);
        Flag flag = new Flag("feature-x", FlagType.BOOLEAN, defaultValue, true, null, List.of(rule));

        provider.setFlag(flag);

        OpenFlagsClient client = OpenFlagsClient.builder().provider(provider).build();
        try {
            EvaluationContext matchCtx = EvaluationContext.builder().attribute("country", "AR").build();
            EvaluationResult<Boolean> result = client.getBooleanResult("feature-x", false, matchCtx);
            assertThat(result.value()).isTrue();
            assertThat(result.reason()).isEqualTo(EvaluationReason.TARGETING_MATCH);

            EvaluationContext noMatchCtx = EvaluationContext.builder().attribute("country", "BR").build();
            EvaluationResult<Boolean> result2 = client.getBooleanResult("feature-x", false, noMatchCtx);
            assertThat(result2.value()).isFalse();
            assertThat(result2.reason()).isEqualTo(EvaluationReason.NO_RULE_MATCHED);
        } finally {
            client.shutdown();
        }
    }

    @Test
    void changeListeners_falseToTrue_emitsEnabled() {
        provider.setBoolean("toggle", false);
        List<FlagChangeEvent> events = new CopyOnWriteArrayList<>();
        provider.addChangeListener(events::add);
        provider.setBoolean("toggle", true);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).changeType()).isEqualTo(ChangeType.ENABLED);
    }

    @Test
    void changeListeners_trueToFalse_emitsDisabled() {
        provider.setBoolean("toggle", true);
        List<FlagChangeEvent> events = new CopyOnWriteArrayList<>();
        provider.addChangeListener(events::add);
        provider.setBoolean("toggle", false);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).changeType()).isEqualTo(ChangeType.DISABLED);
    }

    @Test
    void changeListeners_numberUpdate_emitsUpdated() {
        provider.setNumber("count", 0.0);
        List<FlagChangeEvent> events = new CopyOnWriteArrayList<>();
        provider.addChangeListener(events::add);
        provider.setNumber("count", 1.0);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).changeType()).isEqualTo(ChangeType.UPDATED);
    }

    @Test
    void changeListeners_newBooleanTrueFlag_emitsCreatedNotEnabled() {
        List<FlagChangeEvent> events = new CopyOnWriteArrayList<>();
        provider.addChangeListener(events::add);
        provider.setBoolean("new-flag", true);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).changeType())
                .as("CREATED takes precedence over ENABLED when flag is new")
                .isEqualTo(ChangeType.CREATED);
    }

    @Test
    void changeListeners_removeBooleanTrueFlag_emitsDeletedNotDisabled() {
        provider.setBoolean("old-flag", true);
        List<FlagChangeEvent> events = new CopyOnWriteArrayList<>();
        provider.addChangeListener(events::add);
        provider.remove("old-flag");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).changeType())
                .as("DELETED takes precedence over DISABLED when flag is removed")
                .isEqualTo(ChangeType.DELETED);
    }
}
