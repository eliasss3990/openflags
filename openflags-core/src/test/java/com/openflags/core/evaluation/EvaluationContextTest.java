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
    void toString_redactsTargetingKeyAndAttributeValues() {
        EvaluationContext ctx = EvaluationContext.builder()
                .targetingKey("user-secret-123")
                .attribute("email", "alice@example.com")
                .attribute("ssn", "999-99-9999")
                .build();
        String repr = ctx.toString();
        assertThat(repr).doesNotContain("user-secret-123");
        assertThat(repr).doesNotContain("alice@example.com");
        assertThat(repr).doesNotContain("999-99-9999");
        assertThat(repr).doesNotContain("email");
        assertThat(repr).doesNotContain("ssn");
        assertThat(repr).contains("targetingKey=set");
        assertThat(repr).contains("attributes=2");
    }

    @Test
    void toString_marksMissingTargetingKeyAsNone() {
        EvaluationContext ctx = EvaluationContext.empty();
        assertThat(ctx.toString())
                .contains("targetingKey=none")
                .contains("attributes=0");
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
