package com.openflags.core.model;

import com.openflags.core.evaluation.rule.MultiVariantRule;
import com.openflags.core.evaluation.rule.Rule;
import com.openflags.core.evaluation.rule.SplitRule;
import com.openflags.core.evaluation.rule.TargetingRule;
import com.openflags.core.evaluation.rule.WeightedVariant;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable representation of a feature flag definition.
 * <p>
 * A flag has a unique key, a type, a current value, an enabled/disabled state,
 * optional metadata, and an optional list of targeting/rollout rules.
 * Implemented as a Java record (ADR-009).
 * </p>
 *
 * @param key      unique identifier for this flag; must match {@code ^[a-zA-Z][a-zA-Z0-9._-]*$}
 * @param type     the data type of this flag's value
 * @param value    the current value of the flag
 * @param enabled  whether this flag is active; disabled flags yield the caller's default value
 * @param metadata optional metadata (description, tags, owner, etc.); never null
 * @param rules    optional targeting/rollout rules; never null after construction
 */
public record Flag(
        String key,
        FlagType type,
        FlagValue value,
        boolean enabled,
        Map<String, String> metadata,
        List<Rule> rules
) {
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9._-]*$");

    /**
     * Compact constructor that validates all fields.
     *
     * @throws NullPointerException     if key, type, or value is null
     * @throws IllegalArgumentException if key is blank or does not match the required pattern,
     *                                  if value type does not match the declared type,
     *                                  or if any rule's value type does not match the declared type
     */
    public Flag {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(value, "value must not be null");

        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "key '" + key + "' does not match required pattern ^[a-zA-Z][a-zA-Z0-9._-]*$");
        }
        if (value.getType() != type) {
            throw new IllegalArgumentException(
                    "value type " + value.getType() + " does not match declared type " + type
                            + " for flag '" + key + "'");
        }
        metadata = metadata == null ? Collections.emptyMap() : Collections.unmodifiableMap(metadata);
        rules = rules == null ? List.of() : List.copyOf(rules);

        for (Rule rule : rules) {
            FlagValue rv = ruleValue(rule);
            if (rv != null && rv.getType() != type) {
                throw new IllegalArgumentException(
                        "rule '" + rule.name() + "' value type " + rv.getType()
                                + " does not match flag '" + key + "' type " + type);
            }
            if (rule instanceof MultiVariantRule m) {
                for (WeightedVariant v : m.variants()) {
                    if (v.value().getType() != type) {
                        throw new IllegalArgumentException(
                                "variant in rule '" + m.name() + "' has value type "
                                        + v.value().getType() + " but flag '" + key + "' is " + type);
                    }
                }
            }
        }
    }

    /**
     * Convenience constructor that defaults {@code rules} to an empty list.
     * <p>Use this overload when the flag has no targeting rules.</p>
     *
     * @param key      the flag key
     * @param type     the flag type
     * @param value    the default flag value
     * @param enabled  whether the flag is active
     * @param metadata optional metadata
     */
    public Flag(String key, FlagType type, FlagValue value, boolean enabled, Map<String, String> metadata) {
        this(key, type, value, enabled, metadata, List.of());
    }

    private static FlagValue ruleValue(Rule rule) {
        return switch (rule) {
            case TargetingRule t    -> t.value();
            case SplitRule s        -> s.value();
            case MultiVariantRule m -> null; // validated per variant separately
        };
    }
}
