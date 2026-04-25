package com.openflags.core.evaluation.rule;

import com.openflags.core.model.FlagValue;

import java.util.Objects;

/**
 * A rule that performs a percentage rollout based on consistent hashing of the
 * pair {@code (flagKey, targetingKey)}.
 * <p>
 * The rule matches when:
 * <ol>
 *   <li>{@link com.openflags.core.evaluation.EvaluationContext#getTargetingKey()} is present, and</li>
 *   <li>{@code BucketAllocator.bucket(flagKey, targetingKey) < percentage}.</li>
 * </ol>
 * If no targeting key is present the rule never matches; evaluation continues with
 * the next rule.
 * </p>
 *
 * @param name       human-readable rule name; non-blank
 * @param percentage rollout percentage in the closed range {@code [0, 100]}
 * @param value      the value to return when this rule matches; type must match the flag's type
 */
public record SplitRule(String name, int percentage, FlagValue value) implements Rule {

    /**
     * Compact constructor that validates fields.
     *
     * @throws NullPointerException     if {@code name} or {@code value} is null
     * @throws IllegalArgumentException if {@code name} is blank or {@code percentage}
     *                                  is outside {@code [0, 100]}
     */
    public SplitRule {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException(
                    "percentage must be within [0, 100], got " + percentage);
        }
    }
}
