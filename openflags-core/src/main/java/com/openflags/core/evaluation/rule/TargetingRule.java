package com.openflags.core.evaluation.rule;

import com.openflags.core.model.FlagValue;

import java.util.List;
import java.util.Objects;

/**
 * A rule that returns {@link #value()} when all of its {@link #conditions()}
 * match the evaluation context (logical AND).
 * <p>
 * Use multiple {@code TargetingRule} instances on a flag to express OR.
 * </p>
 *
 * @param name       human-readable rule name; non-blank
 * @param conditions the AND-joined conditions; at least one
 * @param value      the value to return when this rule matches; type must match the flag's type
 */
public record TargetingRule(String name, List<Condition> conditions, FlagValue value) implements Rule {

    /**
     * Compact constructor that validates fields and makes the conditions list immutable.
     *
     * @throws NullPointerException     if any field is null
     * @throws IllegalArgumentException if {@code name} is blank or {@code conditions} is empty
     */
    public TargetingRule {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(conditions, "conditions must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("conditions must not be empty");
        }
        conditions = List.copyOf(conditions);
    }
}
