package com.openflags.core.evaluation.rule;

import com.openflags.core.model.FlagValue;

import java.util.Objects;

/**
 * A single weighted variant inside a {@link MultiVariantRule}.
 * <p>
 * The {@code weight} is an integer percentage in {@code [0, 100]}. The sum of all
 * weights inside a {@link MultiVariantRule} must be exactly {@code 100} (validated
 * at rule construction time).
 * </p>
 * <p>
 * A {@code weight} of {@code 0} is allowed: it temporarily disables a variant
 * without removing it from the list, preserving the relative order (which is
 * relevant for the bucket-to-variant mapping; see {@link VariantSelector}).
 * </p>
 *
 * @param value  the value to return when this variant is selected; non-null
 * @param weight the integer weight in {@code [0, 100]}
 */
public record WeightedVariant(FlagValue value, int weight) {

    /**
     * Compact constructor that validates the field ranges.
     *
     * @throws NullPointerException     if {@code value} is null
     * @throws IllegalArgumentException if {@code weight} is outside {@code [0, 100]}
     */
    public WeightedVariant {
        Objects.requireNonNull(value, "value must not be null");
        if (weight < 0 || weight > 100) {
            throw new IllegalArgumentException(
                    "weight must be within [0, 100], got " + weight);
        }
    }
}
