package com.openflags.core.evaluation.rule;

import com.openflags.core.evaluation.EvaluationContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

class ConditionEvaluatorTest {

    private static EvaluationContext ctx(String key, Object value) {
        return EvaluationContext.builder().attribute(key, value).build();
    }

    private static EvaluationContext ctxWithTargetingKey(String tk) {
        return EvaluationContext.of(tk);
    }

    // EQ

    @Test
    void eq_stringMatch() {
        assertThat(ConditionEvaluator.matches(
                new Condition("country", Operator.EQ, "AR"),
                ctx("country", "AR"))).isTrue();
    }

    @Test
    void eq_stringNoMatch() {
        assertThat(ConditionEvaluator.matches(
                new Condition("country", Operator.EQ, "AR"),
                ctx("country", "BR"))).isFalse();
    }

    @Test
    void eq_numericCoercion() {
        assertThat(ConditionEvaluator.matches(
                new Condition("rate", Operator.EQ, 1.0),
                ctx("rate", 1))).isTrue();
    }

    // NEQ

    @Test
    void neq_match() {
        assertThat(ConditionEvaluator.matches(
                new Condition("country", Operator.NEQ, "AR"),
                ctx("country", "BR"))).isTrue();
    }

    @Test
    void neq_noMatch() {
        assertThat(ConditionEvaluator.matches(
                new Condition("country", Operator.NEQ, "AR"),
                ctx("country", "AR"))).isFalse();
    }

    // IN

    @Test
    void in_match() {
        assertThat(ConditionEvaluator.matches(
                new Condition("plan", Operator.IN, List.of("pro", "enterprise")),
                ctx("plan", "pro"))).isTrue();
    }

    @Test
    void in_noMatch() {
        assertThat(ConditionEvaluator.matches(
                new Condition("plan", Operator.IN, List.of("pro", "enterprise")),
                ctx("plan", "free"))).isFalse();
    }

    @Test
    void in_numericCoercion_intMatchesDouble() {
        assertThat(ConditionEvaluator.matches(
                new Condition("age", Operator.IN, List.of(18.0, 21.0)),
                ctx("age", 18))).isTrue();
    }

    @Test
    void in_numericCoercion_intNotInList() {
        assertThat(ConditionEvaluator.matches(
                new Condition("age", Operator.IN, List.of(19.0, 21.0)),
                ctx("age", 18))).isFalse();
    }

    // NOT_IN

    @Test
    void notIn_numericCoercion_intInList() {
        assertThat(ConditionEvaluator.matches(
                new Condition("age", Operator.NOT_IN, List.of(18.0, 21.0)),
                ctx("age", 18))).isFalse();
    }

    @Test
    void notIn_numericCoercion_intNotInList() {
        assertThat(ConditionEvaluator.matches(
                new Condition("age", Operator.NOT_IN, List.of(19.0, 21.0)),
                ctx("age", 18))).isTrue();
    }

    @Test
    void notIn_match() {
        assertThat(ConditionEvaluator.matches(
                new Condition("country", Operator.NOT_IN, List.of("KP", "IR")),
                ctx("country", "AR"))).isTrue();
    }

    @Test
    void notIn_noMatch() {
        assertThat(ConditionEvaluator.matches(
                new Condition("country", Operator.NOT_IN, List.of("KP", "IR")),
                ctx("country", "KP"))).isFalse();
    }

    // GT / GTE / LT / LTE

    @Test
    void gt_match() {
        assertThat(ConditionEvaluator.matches(
                new Condition("age", Operator.GT, 18.0),
                ctx("age", 25))).isTrue();
    }

    @Test
    void gt_noMatch() {
        assertThat(ConditionEvaluator.matches(
                new Condition("age", Operator.GT, 18.0),
                ctx("age", 18))).isFalse();
    }

    @Test
    void gte_match() {
        assertThat(ConditionEvaluator.matches(
                new Condition("age", Operator.GTE, 18.0),
                ctx("age", 18))).isTrue();
    }

    @Test
    void lt_match() {
        assertThat(ConditionEvaluator.matches(
                new Condition("age", Operator.LT, 18.0),
                ctx("age", 17))).isTrue();
    }

    @Test
    void lte_match() {
        assertThat(ConditionEvaluator.matches(
                new Condition("age", Operator.LTE, 18.0),
                ctx("age", 18))).isTrue();
    }

    @Test
    void numericOp_typeMismatch_returnsFalse() {
        assertThat(ConditionEvaluator.matches(
                new Condition("age", Operator.GT, 18.0),
                ctx("age", "not-a-number"))).isFalse();
    }

    // CONTAINS / STARTS_WITH / ENDS_WITH

    @Test
    void contains_match() {
        assertThat(ConditionEvaluator.matches(
                new Condition("email", Operator.CONTAINS, "@"),
                ctx("email", "user@example.com"))).isTrue();
    }

    @Test
    void contains_noMatch() {
        assertThat(ConditionEvaluator.matches(
                new Condition("email", Operator.CONTAINS, "@"),
                ctx("email", "nodomain"))).isFalse();
    }

    @Test
    void startsWith_match() {
        assertThat(ConditionEvaluator.matches(
                new Condition("email", Operator.STARTS_WITH, "admin"),
                ctx("email", "admin@test.com"))).isTrue();
    }

    @Test
    void endsWith_match() {
        assertThat(ConditionEvaluator.matches(
                new Condition("email", Operator.ENDS_WITH, "@openflags.com"),
                ctx("email", "alice@openflags.com"))).isTrue();
    }

    @Test
    void stringOp_typeMismatch_returnsFalse() {
        assertThat(ConditionEvaluator.matches(
                new Condition("flag", Operator.CONTAINS, "@"),
                ctx("flag", 42))).isFalse();
    }

    // MATCHES

    @Test
    void matches_match() {
        assertThat(ConditionEvaluator.matches(
                new Condition("email", Operator.MATCHES, Pattern.compile(".*\\+beta@example\\.com$")),
                ctx("email", "user+beta@example.com"))).isTrue();
    }

    @Test
    void matches_noMatch() {
        assertThat(ConditionEvaluator.matches(
                new Condition("email", Operator.MATCHES, Pattern.compile(".*\\+beta@example\\.com$")),
                ctx("email", "user@example.com"))).isFalse();
    }

    @Test
    void matches_typeMismatch_returnsFalse() {
        assertThat(ConditionEvaluator.matches(
                new Condition("value", Operator.MATCHES, Pattern.compile(".*")),
                ctx("value", 42))).isFalse();
    }

    @Test
    void matches_partialMatch_patternFoundInSubstring() {
        assertThat(ConditionEvaluator.matches(
                new Condition("code", Operator.MATCHES, Pattern.compile("[0-9]+")),
                ctx("code", "abc123"))).isTrue();
    }

    @Test
    void matches_anchoredPattern_doesNotMatchPartial() {
        assertThat(ConditionEvaluator.matches(
                new Condition("code", Operator.MATCHES, Pattern.compile("^[0-9]+$")),
                ctx("code", "abc123"))).isFalse();
    }

    @Test
    void matches_inputTooLong_returnsFalse() {
        String longInput = "a".repeat(ConditionEvaluator.MAX_MATCH_INPUT_LENGTH + 1);
        assertThat(ConditionEvaluator.matches(
                new Condition("value", Operator.MATCHES, Pattern.compile(".*")),
                ctx("value", longInput))).isFalse();
    }

    // targetingKey attribute

    @Test
    void targetingKey_attribute_resolved() {
        assertThat(ConditionEvaluator.matches(
                new Condition("targetingKey", Operator.EQ, "user-1"),
                ctxWithTargetingKey("user-1"))).isTrue();
    }

    @Test
    void targetingKey_missing_returnsFalse() {
        assertThat(ConditionEvaluator.matches(
                new Condition("targetingKey", Operator.EQ, "user-1"),
                EvaluationContext.empty())).isFalse();
    }

    // Attribute absent

    @Test
    void attributeAbsent_returnsFalse() {
        assertThat(ConditionEvaluator.matches(
                new Condition("nonexistent", Operator.EQ, "val"),
                EvaluationContext.empty())).isFalse();
    }
}
