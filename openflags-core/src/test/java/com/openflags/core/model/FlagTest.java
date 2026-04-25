package com.openflags.core.model;

import com.openflags.core.evaluation.rule.Condition;
import com.openflags.core.evaluation.rule.Operator;
import com.openflags.core.evaluation.rule.SplitRule;
import com.openflags.core.evaluation.rule.TargetingRule;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class FlagTest {

    private static final FlagValue BOOL_VALUE = FlagValue.of(true, FlagType.BOOLEAN);

    @Test
    void constructor_createsValidFlag() {
        Flag flag = new Flag("my-flag", FlagType.BOOLEAN, BOOL_VALUE, true, Map.of());
        assertThat(flag.key()).isEqualTo("my-flag");
        assertThat(flag.type()).isEqualTo(FlagType.BOOLEAN);
        assertThat(flag.value()).isEqualTo(BOOL_VALUE);
        assertThat(flag.enabled()).isTrue();
    }

    @Test
    void constructor_nullMetadataDefaultsToEmptyMap() {
        Flag flag = new Flag("my-flag", FlagType.BOOLEAN, BOOL_VALUE, true, null);
        assertThat(flag.metadata()).isEmpty();
    }

    @Test
    void constructor_metadataIsUnmodifiable() {
        Map<String, String> mutable = new HashMap<>();
        mutable.put("owner", "team-a");
        Flag flag = new Flag("my-flag", FlagType.BOOLEAN, BOOL_VALUE, true, mutable);
        assertThatThrownBy(() -> flag.metadata().put("new", "entry"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructor_throwsWhenKeyIsNull() {
        assertThatThrownBy(() -> new Flag(null, FlagType.BOOLEAN, BOOL_VALUE, true, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsWhenKeyIsBlank() {
        assertThatThrownBy(() -> new Flag("  ", FlagType.BOOLEAN, BOOL_VALUE, true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_throwsWhenKeyHasInvalidPattern() {
        assertThatThrownBy(() -> new Flag("1invalid", FlagType.BOOLEAN, BOOL_VALUE, true, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Flag("has space", FlagType.BOOLEAN, BOOL_VALUE, true, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Flag("has@special", FlagType.BOOLEAN, BOOL_VALUE, true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_acceptsValidKeyPatterns() {
        assertThatCode(() -> new Flag("a", FlagType.BOOLEAN, BOOL_VALUE, true, null)).doesNotThrowAnyException();
        assertThatCode(() -> new Flag("my-flag.v2", FlagType.BOOLEAN, BOOL_VALUE, true, null)).doesNotThrowAnyException();
        assertThatCode(() -> new Flag("feature_123", FlagType.BOOLEAN, BOOL_VALUE, true, null)).doesNotThrowAnyException();
    }

    @Test
    void constructor_throwsWhenTypeIsNull() {
        assertThatThrownBy(() -> new Flag("my-flag", null, BOOL_VALUE, true, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsWhenValueIsNull() {
        assertThatThrownBy(() -> new Flag("my-flag", FlagType.BOOLEAN, null, true, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsOnTypeMismatch() {
        FlagValue stringValue = FlagValue.of("text", FlagType.STRING);
        assertThatThrownBy(() -> new Flag("my-flag", FlagType.BOOLEAN, stringValue, true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STRING")
                .hasMessageContaining("BOOLEAN");
    }

    @Test
    void equalsAndHashCode_workCorrectly() {
        Flag f1 = new Flag("my-flag", FlagType.BOOLEAN, BOOL_VALUE, true, Map.of());
        Flag f2 = new Flag("my-flag", FlagType.BOOLEAN, BOOL_VALUE, true, Map.of());
        Flag f3 = new Flag("other-flag", FlagType.BOOLEAN, BOOL_VALUE, true, Map.of());
        assertThat(f1).isEqualTo(f2).hasSameHashCodeAs(f2);
        assertThat(f1).isNotEqualTo(f3);
    }

    // Phase 2: rules field

    @Test
    void constructor_withoutRules_defaultsToEmpty() {
        Flag flag = new Flag("f", FlagType.BOOLEAN, BOOL_VALUE, true, null);
        assertThat(flag.rules()).isEmpty();
    }

    @Test
    void constructor_nullRules_defaultsToEmpty() {
        Flag flag = new Flag("f", FlagType.BOOLEAN, BOOL_VALUE, true, null, null);
        assertThat(flag.rules()).isEmpty();
    }

    @Test
    void constructor_withRules_isImmutable() {
        TargetingRule rule = new TargetingRule("r",
                List.of(new Condition("x", Operator.EQ, "v")), BOOL_VALUE);
        List<com.openflags.core.evaluation.rule.Rule> mutable = new java.util.ArrayList<>(List.of(rule));
        Flag flag = new Flag("f", FlagType.BOOLEAN, BOOL_VALUE, true, null, mutable);
        mutable.add(rule);
        assertThat(flag.rules()).hasSize(1);
    }

    @Test
    void constructor_throwsWhenRuleValueTypeMismatch() {
        FlagValue stringValue = FlagValue.of("hello", FlagType.STRING);
        TargetingRule rule = new TargetingRule("r",
                List.of(new Condition("x", Operator.EQ, "v")), stringValue);
        assertThatThrownBy(() -> new Flag("f", FlagType.BOOLEAN, BOOL_VALUE, true, null, List.of(rule)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STRING")
                .hasMessageContaining("BOOLEAN");
    }

    @Test
    void constructor_withSplitRule_works() {
        SplitRule rule = new SplitRule("rollout", 50, BOOL_VALUE);
        assertThatCode(() -> new Flag("f", FlagType.BOOLEAN, BOOL_VALUE, true, null, List.of(rule)))
                .doesNotThrowAnyException();
    }
}
