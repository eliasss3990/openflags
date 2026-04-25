package com.openflags.core.evaluation.rule;

import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SplitRuleTest {

    private static final FlagValue BOOL_VALUE = FlagValue.of(true, FlagType.BOOLEAN);

    @Test
    void constructor_createsValidRule() {
        SplitRule rule = new SplitRule("gradual-rollout", 25, BOOL_VALUE);
        assertThat(rule.name()).isEqualTo("gradual-rollout");
        assertThat(rule.percentage()).isEqualTo(25);
        assertThat(rule.value()).isEqualTo(BOOL_VALUE);
    }

    @Test
    void constructor_accepts0and100() {
        assertThatCode(() -> new SplitRule("r", 0, BOOL_VALUE)).doesNotThrowAnyException();
        assertThatCode(() -> new SplitRule("r", 100, BOOL_VALUE)).doesNotThrowAnyException();
    }

    @Test
    void constructor_throwsWhenPercentageNegative() {
        assertThatThrownBy(() -> new SplitRule("r", -1, BOOL_VALUE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[0, 100]");
    }

    @Test
    void constructor_throwsWhenPercentageOver100() {
        assertThatThrownBy(() -> new SplitRule("r", 101, BOOL_VALUE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[0, 100]");
    }

    @Test
    void constructor_throwsWhenNameNull() {
        assertThatThrownBy(() -> new SplitRule(null, 50, BOOL_VALUE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsWhenNameBlank() {
        assertThatThrownBy(() -> new SplitRule("  ", 50, BOOL_VALUE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_throwsWhenValueNull() {
        assertThatThrownBy(() -> new SplitRule("r", 50, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void implementsRule() {
        SplitRule rule = new SplitRule("r", 50, BOOL_VALUE);
        assertThat(rule).isInstanceOf(Rule.class);
        assertThat(rule.name()).isEqualTo("r");
    }
}
