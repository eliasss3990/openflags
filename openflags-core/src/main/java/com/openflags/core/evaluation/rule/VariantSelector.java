package com.openflags.core.evaluation.rule;

import java.util.List;
import java.util.Objects;

/**
 * Maps a bucket value (in {@code [0, 99]}) to one of the weighted variants of a
 * {@link MultiVariantRule} using cumulative weight ranges.
 * <p>
 * Stateless and thread-safe.
 * </p>
 */
public final class VariantSelector {

    private VariantSelector() {}

    /**
     * Selects a variant from the given list based on the bucket value.
     * <p>
     * The selection rule is: starting from the first variant, accumulate weights
     * and return the first variant whose cumulative range
     * {@code [acc - weight, acc)} contains {@code bucket}.
     * </p>
     *
     * @param variants the weighted variants; must be non-empty and weights must sum to 100
     * @param bucket   the bucket in {@code [0, 99]}
     * @return the selected variant; never null
     * @throws NullPointerException     if {@code variants} is null
     * @throws IllegalArgumentException if {@code bucket} is outside {@code [0, 99]}
     * @throws IllegalStateException    if weights do not sum to 100 (defensive guard)
     */
    public static WeightedVariant select(List<WeightedVariant> variants, int bucket) {
        Objects.requireNonNull(variants, "variants must not be null");
        if (bucket < 0 || bucket > 99) {
            throw new IllegalArgumentException(
                    "bucket must be within [0, 99], got " + bucket);
        }
        int cumulative = 0;
        for (WeightedVariant v : variants) {
            cumulative += v.weight();
            if (bucket < cumulative) {
                return v;
            }
        }
        throw new IllegalStateException(
                "No variant selected for bucket " + bucket + "; weights do not sum to 100");
    }
}
