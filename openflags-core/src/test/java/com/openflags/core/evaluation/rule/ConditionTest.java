package com.openflags.core.evaluation.rule;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

class ConditionTest {

    @Test
    void constructor_createsValidCondition() {
        Condition c = new Condition("country", Operator.EQ, "AR");
        assertThat(c.attribute()).isEqualTo("country");
        assertThat(c.operator()).isEqualTo(Operator.EQ);
        assertThat(c.expectedValue()).isEqualTo("AR");
    }

    @Test
    void constructor_throwsWhenAttributeNull() {
        assertThatThrownBy(() -> new Condition(null, Operator.EQ, "AR"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsWhenAttributeBlank() {
        assertThatThrownBy(() -> new Condition("  ", Operator.EQ, "AR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void constructor_throwsWhenOperatorNull() {
        assertThatThrownBy(() -> new Condition("country", null, "AR"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsWhenExpectedValueNull() {
        assertThatThrownBy(() -> new Condition("country", Operator.EQ, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_inOperator_requiresList() {
        assertThatThrownBy(() -> new Condition("plan", Operator.IN, "pro"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("List");
    }

    @Test
    void constructor_notInOperator_requiresList() {
        assertThatThrownBy(() -> new Condition("plan", Operator.NOT_IN, "pro"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("List");
    }

    @Test
    void constructor_inOperator_acceptsList() {
        Condition c = new Condition("plan", Operator.IN, List.of("pro", "enterprise"));
        assertThat(c.expectedValue()).isEqualTo(List.of("pro", "enterprise"));
    }

    @Test
    void constructor_inOperator_copiesList() {
        List<String> mutable = new java.util.ArrayList<>(List.of("pro"));
        Condition c = new Condition("plan", Operator.IN, mutable);
        mutable.add("free");
        assertThat(((List<?>) c.expectedValue())).hasSize(1);
    }

    @Test
    void constructor_matchesOperator_requiresPattern() {
        assertThatThrownBy(() -> new Condition("email", Operator.MATCHES, ".*@test\\.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pattern");
    }

    @Test
    void constructor_matchesOperator_acceptsPattern() {
        Pattern p = Pattern.compile(".*@test\\.com");
        Condition c = new Condition("email", Operator.MATCHES, p);
        assertThat(c.expectedValue()).isEqualTo(p);
    }

    @Test
    void constructor_targetingKeyAttribute_isValid() {
        Condition c = new Condition("targetingKey", Operator.EQ, "user-1");
        assertThat(c.attribute()).isEqualTo("targetingKey");
    }
}
