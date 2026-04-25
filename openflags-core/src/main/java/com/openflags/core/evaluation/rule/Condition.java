package com.openflags.core.evaluation.rule;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An atomic condition applied to a single attribute of an {@link com.openflags.core.evaluation.EvaluationContext}.
 * <p>
 * The special attribute name {@code "targetingKey"} reads from
 * {@link com.openflags.core.evaluation.EvaluationContext#getTargetingKey()} instead of
 * the attributes map. Otherwise, the attribute name is looked up in the attributes map.
 * </p>
 * <p>
 * For {@link Operator#MATCHES}, {@code expectedValue} must be a {@link Pattern} (precompiled at parse time).
 * For {@link Operator#IN} and {@link Operator#NOT_IN}, {@code expectedValue} must be a {@link List}.
 * For all other operators, {@code expectedValue} is the literal scalar to compare against.
 * </p>
 *
 * @param attribute     the attribute name (or {@code "targetingKey"}); non-blank
 * @param operator      the comparison operator; non-null
 * @param expectedValue the expected value; non-null and shape-compatible with {@code operator}
 */
public record Condition(String attribute, Operator operator, Object expectedValue) {

    /**
     * Compact constructor that validates fields and freezes list-typed expected values.
     *
     * @throws NullPointerException     if any field is null
     * @throws IllegalArgumentException if {@code attribute} is blank, or {@code expectedValue}
     *                                  shape is incompatible with {@code operator}
     */
    public Condition {
        Objects.requireNonNull(attribute, "attribute must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(expectedValue, "expectedValue must not be null");
        if (attribute.isBlank()) {
            throw new IllegalArgumentException("attribute must not be blank");
        }
        expectedValue = normalizeExpected(operator, expectedValue);
    }

    private static Object normalizeExpected(Operator op, Object expected) {
        return switch (op) {
            case IN, NOT_IN -> {
                if (!(expected instanceof List)) {
                    throw new IllegalArgumentException(
                            "expectedValue for operator " + op + " must be a List, got "
                                    + expected.getClass().getSimpleName());
                }
                yield List.copyOf((List<?>) expected);
            }
            case MATCHES -> {
                if (!(expected instanceof Pattern)) {
                    throw new IllegalArgumentException(
                            "expectedValue for operator MATCHES must be a precompiled Pattern, got "
                                    + expected.getClass().getSimpleName());
                }
                yield expected;
            }
            default -> expected;
        };
    }
}
