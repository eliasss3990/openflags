package com.openflags.core.evaluation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EvaluationResultTest {

    @Test
    void constructor_storesAllFields() {
        EvaluationResult<Boolean> result = new EvaluationResult<>(true, EvaluationReason.RESOLVED, "dark-mode");
        assertThat(result.value()).isTrue();
        assertThat(result.reason()).isEqualTo(EvaluationReason.RESOLVED);
        assertThat(result.flagKey()).isEqualTo("dark-mode");
    }

    @Test
    void equalsAndHashCode_workCorrectly() {
        EvaluationResult<String> r1 = new EvaluationResult<>("val", EvaluationReason.FLAG_NOT_FOUND, "k");
        EvaluationResult<String> r2 = new EvaluationResult<>("val", EvaluationReason.FLAG_NOT_FOUND, "k");
        EvaluationResult<String> r3 = new EvaluationResult<>("other", EvaluationReason.RESOLVED, "k");
        assertThat(r1).isEqualTo(r2).hasSameHashCodeAs(r2);
        assertThat(r1).isNotEqualTo(r3);
    }

    @Test
    void genericType_worksForAllSupportedTypes() {
        EvaluationResult<Boolean> bool = new EvaluationResult<>(false, EvaluationReason.FLAG_DISABLED, "f");
        EvaluationResult<Double> num = new EvaluationResult<>(3.14, EvaluationReason.RESOLVED, "f");
        EvaluationResult<String> str = new EvaluationResult<>("hello", EvaluationReason.TYPE_MISMATCH, "f");
        assertThat(bool.value()).isFalse();
        assertThat(num.value()).isEqualTo(3.14);
        assertThat(str.value()).isEqualTo("hello");
    }
}
