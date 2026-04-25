package com.openflags.core.evaluation.rule;

import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationReason;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

    private RuleEngine engine;

    private static final FlagValue DEFAULT_VALUE = FlagValue.of(false, FlagType.BOOLEAN);
    private static final FlagValue RULE_VALUE = FlagValue.of(true, FlagType.BOOLEAN);

    @BeforeEach
    void setUp() {
        engine = new RuleEngine();
    }

    private Flag flagWithRules(List<Rule> rules) {
        return new Flag("test-flag", FlagType.BOOLEAN, DEFAULT_VALUE, true, null, rules);
    }

    private Flag flagNoRules() {
        return new Flag("test-flag", FlagType.BOOLEAN, DEFAULT_VALUE, true, null);
    }

    @Test
    void noRules_returnsResolved() {
        RuleEngine.Resolution r = engine.resolve(flagNoRules(), EvaluationContext.empty());
        assertThat(r.value()).isEqualTo(DEFAULT_VALUE);
        assertThat(r.reason()).isEqualTo(EvaluationReason.RESOLVED);
    }

    @Test
    void emptyRulesList_returnsResolved() {
        RuleEngine.Resolution r = engine.resolve(flagWithRules(List.of()), EvaluationContext.empty());
        assertThat(r.value()).isEqualTo(DEFAULT_VALUE);
        assertThat(r.reason()).isEqualTo(EvaluationReason.RESOLVED);
    }

    @Test
    void targetingRule_matches_returnsTargetingMatch() {
        Condition c = new Condition("country", Operator.EQ, "AR");
        TargetingRule rule = new TargetingRule("ar-only", List.of(c), RULE_VALUE);

        EvaluationContext ctx = EvaluationContext.builder().attribute("country", "AR").build();
        RuleEngine.Resolution r = engine.resolve(flagWithRules(List.of(rule)), ctx);

        assertThat(r.value()).isEqualTo(RULE_VALUE);
        assertThat(r.reason()).isEqualTo(EvaluationReason.TARGETING_MATCH);
    }

    @Test
    void targetingRule_noMatch_returnsDefault() {
        Condition c = new Condition("country", Operator.EQ, "AR");
        TargetingRule rule = new TargetingRule("ar-only", List.of(c), RULE_VALUE);

        EvaluationContext ctx = EvaluationContext.builder().attribute("country", "BR").build();
        RuleEngine.Resolution r = engine.resolve(flagWithRules(List.of(rule)), ctx);

        assertThat(r.value()).isEqualTo(DEFAULT_VALUE);
        assertThat(r.reason()).isEqualTo(EvaluationReason.DEFAULT);
    }

    @Test
    void targetingRule_emptyContext_returnsDefault() {
        Condition c = new Condition("country", Operator.EQ, "AR");
        TargetingRule rule = new TargetingRule("ar-only", List.of(c), RULE_VALUE);

        RuleEngine.Resolution r = engine.resolve(flagWithRules(List.of(rule)), EvaluationContext.empty());

        assertThat(r.reason()).isEqualTo(EvaluationReason.DEFAULT);
    }

    @Test
    void splitRule_withTargetingKey_matchesBucket() {
        // percentage=100 always matches
        SplitRule rule = new SplitRule("always-split", 100, RULE_VALUE);

        EvaluationContext ctx = EvaluationContext.of("some-user");
        RuleEngine.Resolution r = engine.resolve(flagWithRules(List.of(rule)), ctx);

        assertThat(r.value()).isEqualTo(RULE_VALUE);
        assertThat(r.reason()).isEqualTo(EvaluationReason.SPLIT);
    }

    @Test
    void splitRule_noTargetingKey_skipsRule() {
        SplitRule rule = new SplitRule("rollout", 100, RULE_VALUE);

        RuleEngine.Resolution r = engine.resolve(flagWithRules(List.of(rule)), EvaluationContext.empty());

        assertThat(r.value()).isEqualTo(DEFAULT_VALUE);
        assertThat(r.reason()).isEqualTo(EvaluationReason.DEFAULT);
    }

    @Test
    void splitRule_percentage0_neverMatches() {
        SplitRule rule = new SplitRule("never-rollout", 0, RULE_VALUE);

        EvaluationContext ctx = EvaluationContext.of("user-1");
        RuleEngine.Resolution r = engine.resolve(flagWithRules(List.of(rule)), ctx);

        assertThat(r.reason()).isEqualTo(EvaluationReason.DEFAULT);
    }

    @Test
    void firstMatchWins() {
        FlagValue firstValue = FlagValue.of("first", FlagType.STRING);
        FlagValue secondValue = FlagValue.of("second", FlagType.STRING);
        Flag flag = new Flag("str-flag", FlagType.STRING,
                FlagValue.of("default", FlagType.STRING), true, null,
                List.of(
                        new TargetingRule("rule1", List.of(new Condition("x", Operator.EQ, "v")), firstValue),
                        new TargetingRule("rule2", List.of(new Condition("x", Operator.EQ, "v")), secondValue)
                ));

        EvaluationContext ctx = EvaluationContext.builder().attribute("x", "v").build();
        RuleEngine.Resolution r = engine.resolve(flag, ctx);

        assertThat(r.value()).isEqualTo(firstValue);
        assertThat(r.reason()).isEqualTo(EvaluationReason.TARGETING_MATCH);
    }
}
