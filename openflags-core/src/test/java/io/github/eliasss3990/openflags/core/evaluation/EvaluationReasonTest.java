package io.github.eliasss3990.openflags.core.evaluation;

import org.junit.jupiter.api.Test;

import static io.github.eliasss3990.openflags.core.evaluation.EvaluationReason.VARIANT;
import static org.assertj.core.api.Assertions.assertThat;

class EvaluationReasonTest {

    @Test
    void containsVariant() {
        assertThat(EvaluationReason.values()).contains(VARIANT);
    }
}
