package com.openflags.core.evaluation;

import com.openflags.core.evaluation.rule.Condition;
import com.openflags.core.evaluation.rule.Operator;
import com.openflags.core.evaluation.rule.SplitRule;
import com.openflags.core.evaluation.rule.TargetingRule;
import com.openflags.core.evaluation.rule.RuleEngine;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.metrics.MetricsRecorder;
import com.openflags.core.metrics.Tag;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import com.openflags.core.provider.FlagProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlagEvaluatorTest {

    @Mock
    private FlagProvider provider;

    private FlagEvaluator evaluator;
    private final EvaluationContext ctx = EvaluationContext.empty();

    @BeforeEach
    void setUp() {
        evaluator = new FlagEvaluator();
    }

    @Test
    void evaluate_resolvesBooleanFlag() {
        Flag flag = new Flag("dark-mode", FlagType.BOOLEAN, FlagValue.of(true, FlagType.BOOLEAN), true, null);
        when(provider.getFlag("dark-mode")).thenReturn(Optional.of(flag));

        EvaluationResult<Boolean> result = evaluator.evaluate(provider, "dark-mode", Boolean.class, false, ctx);

        assertThat(result.value()).isTrue();
        assertThat(result.reason()).isEqualTo(EvaluationReason.RESOLVED);
        assertThat(result.flagKey()).isEqualTo("dark-mode");
    }

    @Test
    void evaluate_returnsDefaultWhenFlagNotFound() {
        when(provider.getFlag("unknown")).thenReturn(Optional.empty());

        EvaluationResult<Boolean> result = evaluator.evaluate(provider, "unknown", Boolean.class, false, ctx);

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.FLAG_NOT_FOUND);
    }

    @Test
    void evaluate_returnsDefaultWhenFlagDisabled() {
        Flag flag = new Flag("disabled-flag", FlagType.BOOLEAN, FlagValue.of(true, FlagType.BOOLEAN), false, null);
        when(provider.getFlag("disabled-flag")).thenReturn(Optional.of(flag));

        EvaluationResult<Boolean> result = evaluator.evaluate(provider, "disabled-flag", Boolean.class, false, ctx);

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.FLAG_DISABLED);
    }

    @Test
    void evaluate_returnsDefaultOnTypeMismatch() {
        Flag flag = new Flag("my-flag", FlagType.STRING, FlagValue.of("hello", FlagType.STRING), true, null);
        when(provider.getFlag("my-flag")).thenReturn(Optional.of(flag));

        EvaluationResult<Boolean> result = evaluator.evaluate(provider, "my-flag", Boolean.class, false, ctx);

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.TYPE_MISMATCH);
    }

    @Test
    void evaluate_returnsDefaultOnProviderException() {
        when(provider.getFlag("my-flag")).thenThrow(new ProviderException("IO error"));

        EvaluationResult<Boolean> result = evaluator.evaluate(provider, "my-flag", Boolean.class, false, ctx);

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.PROVIDER_ERROR);
    }

    @Test
    void evaluate_returnsDefaultOnUnexpectedException() {
        when(provider.getFlag("my-flag")).thenThrow(new RuntimeException("unexpected"));

        EvaluationResult<Boolean> result = evaluator.evaluate(provider, "my-flag", Boolean.class, false, ctx);

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.PROVIDER_ERROR);
    }

    @Test
    void evaluate_recordsUnexpectedProviderError_metric() {
        RecordingMetricsRecorder recorder = new RecordingMetricsRecorder();
        FlagEvaluator instrumented = new FlagEvaluator(new RuleEngine(), recorder);
        when(provider.getFlag("my-flag")).thenThrow(new IllegalStateException("boom"));

        EvaluationResult<Boolean> result = instrumented.evaluate(provider, "my-flag", Boolean.class, false, ctx);

        assertThat(result.reason()).isEqualTo(EvaluationReason.PROVIDER_ERROR);
        assertThat(recorder.unexpectedErrors).containsExactly("my-flag");
    }

    @Test
    void evaluate_doesNotRecordUnexpected_onProviderException() {
        RecordingMetricsRecorder recorder = new RecordingMetricsRecorder();
        FlagEvaluator instrumented = new FlagEvaluator(new RuleEngine(), recorder);
        when(provider.getFlag("my-flag")).thenThrow(new ProviderException("expected"));

        instrumented.evaluate(provider, "my-flag", Boolean.class, false, ctx);

        assertThat(recorder.unexpectedErrors).isEmpty();
    }

    private static final class RecordingMetricsRecorder implements MetricsRecorder {
        final java.util.List<String> unexpectedErrors = new java.util.ArrayList<>();

        @Override public void recordEvaluation(EvaluationEvent event) {}
        @Override public void recordPoll(String outcome, long durationNanos) {}
        @Override public void recordSnapshotWrite(String outcome, long durationNanos) {}
        @Override public void recordFlagChange(com.openflags.core.event.ChangeType type) {}
        @Override public void recordHybridFallback(String from, String to) {}
        @Override public void recordListenerError(String listenerSimpleName) {}
        @Override public void recordUnexpectedProviderError(String flagKey) { unexpectedErrors.add(flagKey); }
        @Override public void registerGauge(String name, Iterable<Tag> tags, java.util.function.Supplier<Number> supplier) {}
    }

    @Test
    void evaluate_resolvesStringFlag() {
        Flag flag = new Flag("theme", FlagType.STRING, FlagValue.of("dark", FlagType.STRING), true, null);
        when(provider.getFlag("theme")).thenReturn(Optional.of(flag));

        EvaluationResult<String> result = evaluator.evaluate(provider, "theme", String.class, "light", ctx);

        assertThat(result.value()).isEqualTo("dark");
        assertThat(result.reason()).isEqualTo(EvaluationReason.RESOLVED);
    }

    @Test
    void evaluate_resolvesNumberFlag() {
        Flag flag = new Flag("rate", FlagType.NUMBER, FlagValue.of(0.25, FlagType.NUMBER), true, null);
        when(provider.getFlag("rate")).thenReturn(Optional.of(flag));

        EvaluationResult<Double> result = evaluator.evaluate(provider, "rate", Double.class, 0.0, ctx);

        assertThat(result.value()).isEqualTo(0.25);
        assertThat(result.reason()).isEqualTo(EvaluationReason.RESOLVED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void evaluate_resolvesObjectFlag() {
        Map<String, Object> config = Map.of("timeout", 30);
        Flag flag = new Flag("config", FlagType.OBJECT, FlagValue.of(config, FlagType.OBJECT), true, null);
        when(provider.getFlag("config")).thenReturn(Optional.of(flag));

        Class<Map<String, Object>> mapType = (Class<Map<String, Object>>) (Class<?>) Map.class;
        EvaluationResult<Map<String, Object>> result = evaluator.evaluate(provider, "config", mapType, Map.of(), ctx);

        assertThat(result.value()).containsEntry("timeout", 30);
        assertThat(result.reason()).isEqualTo(EvaluationReason.RESOLVED);
    }

    // Phase 2: rule engine integration

    @Test
    void evaluate_withTargetingRule_matchingContext_returnsTargetingMatch() {
        FlagValue ruleValue = FlagValue.of(true, FlagType.BOOLEAN);
        TargetingRule rule = new TargetingRule("ar-only",
                List.of(new Condition("country", Operator.EQ, "AR")), ruleValue);
        Flag flag = new Flag("feature-x", FlagType.BOOLEAN,
                FlagValue.of(false, FlagType.BOOLEAN), true, null, List.of(rule));
        when(provider.getFlag("feature-x")).thenReturn(Optional.of(flag));

        EvaluationContext matchCtx = EvaluationContext.builder().attribute("country", "AR").build();
        EvaluationResult<Boolean> result = evaluator.evaluate(provider, "feature-x", Boolean.class, false, matchCtx);

        assertThat(result.value()).isTrue();
        assertThat(result.reason()).isEqualTo(EvaluationReason.TARGETING_MATCH);
    }

    @Test
    void evaluate_withTargetingRule_noMatch_returnsDefault() {
        FlagValue ruleValue = FlagValue.of(true, FlagType.BOOLEAN);
        TargetingRule rule = new TargetingRule("ar-only",
                List.of(new Condition("country", Operator.EQ, "AR")), ruleValue);
        Flag flag = new Flag("feature-x", FlagType.BOOLEAN,
                FlagValue.of(false, FlagType.BOOLEAN), true, null, List.of(rule));
        when(provider.getFlag("feature-x")).thenReturn(Optional.of(flag));

        EvaluationContext noMatchCtx = EvaluationContext.builder().attribute("country", "BR").build();
        EvaluationResult<Boolean> result = evaluator.evaluate(provider, "feature-x", Boolean.class, false, noMatchCtx);

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.NO_RULE_MATCHED);
    }

    @Test
    void evaluate_withSplitRule_percentage100_returnsSplit() {
        FlagValue ruleValue = FlagValue.of(true, FlagType.BOOLEAN);
        SplitRule rule = new SplitRule("always-rollout", 100, ruleValue);
        Flag flag = new Flag("new-checkout", FlagType.BOOLEAN,
                FlagValue.of(false, FlagType.BOOLEAN), true, null, List.of(rule));
        when(provider.getFlag("new-checkout")).thenReturn(Optional.of(flag));

        EvaluationContext ctxWithKey = EvaluationContext.of("user-123");
        EvaluationResult<Boolean> result = evaluator.evaluate(provider, "new-checkout", Boolean.class, false, ctxWithKey);

        assertThat(result.value()).isTrue();
        assertThat(result.reason()).isEqualTo(EvaluationReason.SPLIT);
    }

    @Test
    void evaluate_withRules_emptyContext_returnsDefault() {
        FlagValue ruleValue = FlagValue.of(true, FlagType.BOOLEAN);
        TargetingRule rule = new TargetingRule("ar-only",
                List.of(new Condition("country", Operator.EQ, "AR")), ruleValue);
        Flag flag = new Flag("feature-x", FlagType.BOOLEAN,
                FlagValue.of(false, FlagType.BOOLEAN), true, null, List.of(rule));
        when(provider.getFlag("feature-x")).thenReturn(Optional.of(flag));

        EvaluationResult<Boolean> result = evaluator.evaluate(provider, "feature-x", Boolean.class, false, ctx);

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.NO_RULE_MATCHED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void evaluate_resolvesObjectFlagWithSubclassOfMap() {
        Map<String, Object> config = Map.of("timeout", 30);
        Flag flag = new Flag("config", FlagType.OBJECT, FlagValue.of(config, FlagType.OBJECT), true, null);

        Class<HashMap<String, Object>> hashMapType = (Class<HashMap<String, Object>>) (Class<?>) HashMap.class;
        when(provider.getFlag("config")).thenReturn(Optional.of(flag));

        EvaluationResult<HashMap<String, Object>> resultHashMap = evaluator.evaluate(
                provider, "config", hashMapType, new HashMap<>(), ctx);

        assertThat(resultHashMap.reason()).isEqualTo(EvaluationReason.RESOLVED);
        assertThat(resultHashMap.value()).containsEntry("timeout", 30);

        Class<LinkedHashMap<String, Object>> linkedHashMapType = (Class<LinkedHashMap<String, Object>>) (Class<?>) LinkedHashMap.class;
        when(provider.getFlag("config")).thenReturn(Optional.of(flag));

        EvaluationResult<LinkedHashMap<String, Object>> resultLinkedHashMap = evaluator.evaluate(
                provider, "config", linkedHashMapType, new LinkedHashMap<>(), ctx);

        assertThat(resultLinkedHashMap.reason()).isEqualTo(EvaluationReason.RESOLVED);
        assertThat(resultLinkedHashMap.value()).containsEntry("timeout", 30);
    }
}