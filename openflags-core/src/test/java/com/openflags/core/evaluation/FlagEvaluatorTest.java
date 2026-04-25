package com.openflags.core.evaluation;

import com.openflags.core.exception.ProviderException;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    @SuppressWarnings("rawtypes")
    void evaluate_resolvesObjectFlag() {
        Map<String, Object> config = Map.of("timeout", 30);
        Flag flag = new Flag("config", FlagType.OBJECT, FlagValue.of(config, FlagType.OBJECT), true, null);
        when(provider.getFlag("config")).thenReturn(Optional.of(flag));

        EvaluationResult<Map> result = evaluator.evaluate(provider, "config", Map.class, Map.of(), ctx);

        assertThat(result.value()).containsEntry("timeout", 30);
        assertThat(result.reason()).isEqualTo(EvaluationReason.RESOLVED);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void evaluate_resolvesObjectFlagWithSubclassOfMap() {
        Map<String, Object> config = Map.of("timeout", 30);
        Flag flag = new Flag("config", FlagType.OBJECT, FlagValue.of(config, FlagType.OBJECT), true, null);
        when(provider.getFlag("config")).thenReturn(Optional.of(flag));

        EvaluationResult<HashMap> resultHashMap = evaluator.evaluate(
                provider, "config", HashMap.class, new HashMap<>(), ctx);
        assertThat(resultHashMap.reason()).isEqualTo(EvaluationReason.RESOLVED);
        assertThat(resultHashMap.value()).containsEntry("timeout", 30);

        when(provider.getFlag("config")).thenReturn(Optional.of(flag));
        EvaluationResult<LinkedHashMap> resultLinkedHashMap = evaluator.evaluate(
                provider, "config", LinkedHashMap.class, new LinkedHashMap<>(), ctx);
        assertThat(resultLinkedHashMap.reason()).isEqualTo(EvaluationReason.RESOLVED);
        assertThat(resultLinkedHashMap.value()).containsEntry("timeout", 30);
    }
}
