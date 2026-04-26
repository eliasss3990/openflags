package com.openflags.core.evaluation;

import org.junit.jupiter.api.Test;

import static com.openflags.core.evaluation.EvaluationReason.VARIANT;
import static org.assertj.core.api.Assertions.assertThat;

class EvaluationReasonTest {

    @Test
    void containsVariant() {
        assertThat(EvaluationReason.values()).contains(VARIANT);
    }
}
