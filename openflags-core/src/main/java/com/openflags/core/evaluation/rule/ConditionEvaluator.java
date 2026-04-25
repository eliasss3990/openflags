package com.openflags.core.evaluation.rule;

import com.openflags.core.evaluation.EvaluationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stateless evaluator for a single {@link Condition} against an {@link EvaluationContext}.
 * <p>
 * Type-tolerant: incompatible types between the actual attribute and the expected value
 * yield {@code false} (logged at DEBUG) rather than throwing.
 * </p>
 */
public final class ConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);

    /** Maximum length of string input for MATCHES operator to prevent ReDoS degradation. */
    static final int MAX_MATCH_INPUT_LENGTH = 4096;

    private ConditionEvaluator() {}

    /**
     * Evaluates a single condition.
     *
     * @param condition the condition; must not be null
     * @param context   the evaluation context; must not be null
     * @return {@code true} if the condition matches, {@code false} otherwise
     * @throws NullPointerException if any argument is null
     */
    public static boolean matches(Condition condition, EvaluationContext context) {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(context, "context must not be null");

        Object actual = resolveAttribute(condition.attribute(), context);
        if (actual == null) {
            log.debug("Attribute '{}' not present in context, condition evaluates to false",
                    condition.attribute());
            return false;
        }

        return evaluate(condition.operator(), actual, condition.expectedValue());
    }

    private static Object resolveAttribute(String attribute, EvaluationContext context) {
        if ("targetingKey".equals(attribute)) {
            return context.getTargetingKey().orElse(null);
        }
        return context.getAttributes().get(attribute);
    }

    private static boolean evaluate(Operator op, Object actual, Object expected) {
        return switch (op) {
            case EQ -> evalEq(actual, expected);
            case NEQ -> !evalEq(actual, expected);
            case IN -> evalIn(actual, expected);
            case NOT_IN -> !evalIn(actual, expected);
            case GT -> evalNumericOp(actual, expected, c -> c > 0);
            case GTE -> evalNumericOp(actual, expected, c -> c >= 0);
            case LT -> evalNumericOp(actual, expected, c -> c < 0);
            case LTE -> evalNumericOp(actual, expected, c -> c <= 0);
            case CONTAINS -> evalContains(actual, expected);
            case STARTS_WITH -> evalStartsWith(actual, expected);
            case ENDS_WITH -> evalEndsWith(actual, expected);
            case MATCHES -> evalMatches(actual, expected);
        };
    }

    private static boolean evalEq(Object actual, Object expected) {
        if (actual instanceof Number a && expected instanceof Number e) {
            return Double.compare(a.doubleValue(), e.doubleValue()) == 0;
        }
        return Objects.equals(actual, expected);
    }

    @SuppressWarnings("unchecked")
    private static boolean evalIn(Object actual, Object expected) {
        if (!(expected instanceof List)) {
            log.debug("IN operator expected a List, got {}", expected.getClass().getSimpleName());
            return false;
        }
        List<Object> list = (List<Object>) expected;
        String actualStr = String.valueOf(actual);
        return list.stream().anyMatch(item -> String.valueOf(item).equals(actualStr));
    }

    private static boolean evalNumericOp(Object actual, Object expected,
            java.util.function.IntPredicate comparePredicate) {
        if (!(actual instanceof Number) || !(expected instanceof Number)) {
            log.debug("Numeric operator requires Number types, got {} and {}",
                    actual.getClass().getSimpleName(), expected.getClass().getSimpleName());
            return false;
        }
        int cmp = Double.compare(((Number) actual).doubleValue(), ((Number) expected).doubleValue());
        return comparePredicate.test(cmp);
    }

    private static boolean evalContains(Object actual, Object expected) {
        if (!(actual instanceof String a) || !(expected instanceof String e)) {
            log.debug("CONTAINS operator requires String types");
            return false;
        }
        return a.contains(e);
    }

    private static boolean evalStartsWith(Object actual, Object expected) {
        if (!(actual instanceof String a) || !(expected instanceof String e)) {
            log.debug("STARTS_WITH operator requires String types");
            return false;
        }
        return a.startsWith(e);
    }

    private static boolean evalEndsWith(Object actual, Object expected) {
        if (!(actual instanceof String a) || !(expected instanceof String e)) {
            log.debug("ENDS_WITH operator requires String types");
            return false;
        }
        return a.endsWith(e);
    }

    private static boolean evalMatches(Object actual, Object expected) {
        if (!(actual instanceof String actualStr)) {
            log.debug("MATCHES operator requires String actual value, got {}",
                    actual.getClass().getSimpleName());
            return false;
        }
        if (actualStr.length() > MAX_MATCH_INPUT_LENGTH) {
            log.debug("MATCHES input exceeds MAX_MATCH_INPUT_LENGTH ({}), condition evaluates to false",
                    MAX_MATCH_INPUT_LENGTH);
            return false;
        }
        Pattern pattern = (Pattern) expected;
        return pattern.matcher(actualStr).find();
    }
}
