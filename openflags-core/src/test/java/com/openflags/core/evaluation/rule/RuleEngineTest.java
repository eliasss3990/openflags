package com.openflags.core.evaluation.rule;

import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationReason;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // ── MultiVariantRule tests ──────────────────────────────────────────────

    @Test
    void multiVariantRule_withTargetingKey_returnsVariant() {
        MultiVariantRule rule = new MultiVariantRule("experiment", List.of(
                new WeightedVariant(DEFAULT_VALUE, 50),
                new WeightedVariant(RULE_VALUE, 50)));

        EvaluationContext ctx = EvaluationContext.of("user-abc");
        RuleEngine.Resolution r = engine.resolve(flagWithRules(List.of(rule)), ctx);

        assertThat(r.reason()).isEqualTo(EvaluationReason.VARIANT);
        // value must be one of the variants
        assertThat(r.value()).isIn(DEFAULT_VALUE, RULE_VALUE);
    }

    @Test
    void multiVariantRule_withoutTargetingKey_skipsRule() {
        MultiVariantRule rule = new MultiVariantRule("experiment", List.of(
                new WeightedVariant(RULE_VALUE, 100)));

        RuleEngine.Resolution r = engine.resolve(flagWithRules(List.of(rule)), EvaluationContext.empty());

        assertThat(r.reason()).isEqualTo(EvaluationReason.DEFAULT);
        assertThat(r.value()).isEqualTo(DEFAULT_VALUE);
    }

    @Test
    void targetingBeforeMultiVariant_firstMatchWinsForTargeting() {
        Condition c = new Condition("role", Operator.EQ, "admin");
        TargetingRule targeting = new TargetingRule("admin-override", List.of(c), RULE_VALUE);
        MultiVariantRule mvr = new MultiVariantRule("experiment", List.of(
                new WeightedVariant(DEFAULT_VALUE, 100)));

        Flag flag = new Flag("test-flag", FlagType.BOOLEAN, DEFAULT_VALUE, true, null,
                List.of(targeting, mvr));

        EvaluationContext ctx = EvaluationContext.builder()
                .targetingKey("user-1")
                .attribute("role", "admin")
                .build();
        RuleEngine.Resolution r = engine.resolve(flag, ctx);

        assertThat(r.reason()).isEqualTo(EvaluationReason.TARGETING_MATCH);
        assertThat(r.value()).isEqualTo(RULE_VALUE);
    }

    @Test
    void multiVariantRule_distributionWithinOnePct() {
        // Use STRING flag with 3 distinct values for A/B/C distribution
        FlagValue defStr = FlagValue.of("default", FlagType.STRING);
        FlagValue varA   = FlagValue.of("A", FlagType.STRING);
        FlagValue varB   = FlagValue.of("B", FlagType.STRING);
        FlagValue varC   = FlagValue.of("C", FlagType.STRING);
        MultiVariantRule rule = new MultiVariantRule("experiment", List.of(
                new WeightedVariant(varA, 30),
                new WeightedVariant(varB, 50),
                new WeightedVariant(varC, 20)));

        Flag flag = new Flag("str-flag", FlagType.STRING, defStr, true, null, List.of(rule));

        int countA = 0, countB = 0, countC = 0;
        int total = 100_000;
        for (int i = 0; i < total; i++) {
            EvaluationContext ctx = EvaluationContext.of("user-" + i);
            RuleEngine.Resolution r = engine.resolve(flag, ctx);
            if (r.value().equals(varA)) countA++;
            else if (r.value().equals(varB)) countB++;
            else countC++;
        }
        double pctA = (double) countA / total * 100;
        double pctB = (double) countB / total * 100;
        double pctC = (double) countC / total * 100;

        assertThat(pctA).isBetween(29.0, 31.0);
        assertThat(pctB).isBetween(49.0, 51.0);
        assertThat(pctC).isBetween(19.0, 21.0);
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
