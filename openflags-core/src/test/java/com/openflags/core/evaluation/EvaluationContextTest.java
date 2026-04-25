package com.openflags.core.evaluation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EvaluationContextTest {

    @Test
    void empty_hasNoTargetingKeyOrAttributes() {
        EvaluationContext ctx = EvaluationContext.empty();
        assertThat(ctx.getTargetingKey()).isEmpty();
        assertThat(ctx.getAttributes()).isEmpty();
    }

    @Test
    void of_setsTargetingKey() {
        EvaluationContext ctx = EvaluationContext.of("user-123");
        assertThat(ctx.getTargetingKey()).contains("user-123");
        assertThat(ctx.getAttributes()).isEmpty();
    }

    @Test
    void of_throwsWhenKeyIsNull() {
        assertThatThrownBy(() -> EvaluationContext.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builder_setsAllFields() {
        EvaluationContext ctx = EvaluationContext.builder()
                .targetingKey("user-456")
                .attribute("country", "US")
                .attribute("plan", "pro")
                .build();
        assertThat(ctx.getTargetingKey()).contains("user-456");
        assertThat(ctx.getAttributes()).containsEntry("country", "US").containsEntry("plan", "pro");
    }

    @Test
    void builder_withoutTargetingKey() {
        EvaluationContext ctx = EvaluationContext.builder()
                .attribute("role", "admin")
                .build();
        assertThat(ctx.getTargetingKey()).isEmpty();
        assertThat(ctx.getAttributes()).containsEntry("role", "admin");
    }

    @Test
    void attributes_areUnmodifiable() {
        EvaluationContext ctx = EvaluationContext.builder().attribute("k", "v").build();
        assertThatThrownBy(() -> ctx.getAttributes().put("new", "entry"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void equalsAndHashCode_workCorrectly() {
        EvaluationContext c1 = EvaluationContext.of("u1");
        EvaluationContext c2 = EvaluationContext.of("u1");
        EvaluationContext c3 = EvaluationContext.of("u2");
        assertThat(c1).isEqualTo(c2).hasSameHashCodeAs(c2);
        assertThat(c1).isNotEqualTo(c3);
    }
}
