package com.openflags.core.evaluation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EvaluationResultTest {

    @Test
    void of_factory_storesAllFields_withNullVariantAndRuleId() {
        EvaluationResult<Boolean> result = EvaluationResult.of(true, EvaluationReason.RESOLVED, "dark-mode");
        assertThat(result.value()).isTrue();
        assertThat(result.reason()).isEqualTo(EvaluationReason.RESOLVED);
        assertThat(result.flagKey()).isEqualTo("dark-mode");
        assertThat(result.variant()).isNull();
        assertThat(result.matchedRuleId()).isNull();
    }

    @Test
    void constructor_storesAllFiveFields() {
        EvaluationResult<String> result = new EvaluationResult<>(
                "treatment-a", EvaluationReason.VARIANT, "experiment-flag", "treatment-a", "ab-experiment");
        assertThat(result.value()).isEqualTo("treatment-a");
        assertThat(result.reason()).isEqualTo(EvaluationReason.VARIANT);
        assertThat(result.flagKey()).isEqualTo("experiment-flag");
        assertThat(result.variant()).isEqualTo("treatment-a");
        assertThat(result.matchedRuleId()).isEqualTo("ab-experiment");
    }

    @Test
    void equalsAndHashCode_includeAllFiveFields() {
        EvaluationResult<String> r1 = new EvaluationResult<>("val", EvaluationReason.FLAG_NOT_FOUND, "k", null, null);
        EvaluationResult<String> r2 = new EvaluationResult<>("val", EvaluationReason.FLAG_NOT_FOUND, "k", null, null);
        EvaluationResult<String> r3 = new EvaluationResult<>("val", EvaluationReason.FLAG_NOT_FOUND, "k", "v", null);
        EvaluationResult<String> r4 = new EvaluationResult<>("other", EvaluationReason.RESOLVED, "k", null, null);
        assertThat(r1).isEqualTo(r2).hasSameHashCodeAs(r2);
        assertThat(r1).isNotEqualTo(r3);
        assertThat(r1).isNotEqualTo(r4);
    }

    @Test
    void genericType_worksForAllSupportedTypes() {
        EvaluationResult<Boolean> bool = EvaluationResult.of(false, EvaluationReason.FLAG_DISABLED, "f");
        EvaluationResult<Double> num  = EvaluationResult.of(3.14, EvaluationReason.RESOLVED, "f");
        EvaluationResult<String> str  = EvaluationResult.of("hello", EvaluationReason.TYPE_MISMATCH, "f");
        assertThat(bool.value()).isFalse();
        assertThat(num.value()).isEqualTo(3.14);
        assertThat(str.value()).isEqualTo("hello");
    }

    @Test
    void errorPaths_haveNullVariantAndMatchedRuleId() {
        EvaluationResult<Boolean> notFound = EvaluationResult.of(false, EvaluationReason.FLAG_NOT_FOUND, "k");
        EvaluationResult<Boolean> disabled = EvaluationResult.of(false, EvaluationReason.FLAG_DISABLED, "k");
        EvaluationResult<Boolean> mismatch = EvaluationResult.of(false, EvaluationReason.TYPE_MISMATCH, "k");
        EvaluationResult<Boolean> error    = EvaluationResult.of(false, EvaluationReason.PROVIDER_ERROR, "k");
        for (EvaluationResult<Boolean> r : java.util.List.of(notFound, disabled, mismatch, error)) {
            assertThat(r.variant()).isNull();
            assertThat(r.matchedRuleId()).isNull();
        }
    }
}
