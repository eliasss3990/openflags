package com.openflags.core.evaluation.rule;

import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TargetingRuleTest {

    private static final FlagValue BOOL_VALUE = FlagValue.of(true, FlagType.BOOLEAN);
    private static final Condition CONDITION = new Condition("country", Operator.EQ, "AR");

    @Test
    void constructor_createsValidRule() {
        TargetingRule rule = new TargetingRule("ar-only", List.of(CONDITION), BOOL_VALUE);
        assertThat(rule.name()).isEqualTo("ar-only");
        assertThat(rule.conditions()).containsExactly(CONDITION);
        assertThat(rule.value()).isEqualTo(BOOL_VALUE);
    }

    @Test
    void constructor_conditionsIsImmutable() {
        List<Condition> mutable = new java.util.ArrayList<>(List.of(CONDITION));
        TargetingRule rule = new TargetingRule("r", mutable, BOOL_VALUE);
        mutable.add(CONDITION);
        assertThat(rule.conditions()).hasSize(1);
    }

    @Test
    void constructor_throwsWhenNameNull() {
        assertThatThrownBy(() -> new TargetingRule(null, List.of(CONDITION), BOOL_VALUE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsWhenNameBlank() {
        assertThatThrownBy(() -> new TargetingRule("  ", List.of(CONDITION), BOOL_VALUE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_throwsWhenConditionsNull() {
        assertThatThrownBy(() -> new TargetingRule("r", null, BOOL_VALUE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsWhenConditionsEmpty() {
        assertThatThrownBy(() -> new TargetingRule("r", List.of(), BOOL_VALUE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void constructor_throwsWhenValueNull() {
        assertThatThrownBy(() -> new TargetingRule("r", List.of(CONDITION), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void implementsRule() {
        TargetingRule rule = new TargetingRule("r", List.of(CONDITION), BOOL_VALUE);
        assertThat(rule).isInstanceOf(Rule.class);
        assertThat(rule.name()).isEqualTo("r");
    }
}
