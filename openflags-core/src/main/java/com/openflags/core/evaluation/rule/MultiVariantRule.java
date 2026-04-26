package com.openflags.core.evaluation.rule;

import java.util.List;
import java.util.Objects;

/**
 * A rule that splits traffic across multiple weighted variants.
 * <p>
 * Selection is deterministic: given {@code (flagKey, targetingKey)} the
 * {@link BucketAllocator} computes a bucket in {@code [0, 99]} which is then
 * mapped to the variant whose cumulative weight range contains the bucket.
 * See {@link VariantSelector#select(List, int)}.
 * </p>
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>If {@code targetingKey} is absent in the {@link com.openflags.core.evaluation.EvaluationContext},
 *       the rule does not match and evaluation continues with the next rule.</li>
 *   <li>If {@code targetingKey} is present, the rule always matches and produces
 *       one of the variants. Subsequent rules are not evaluated.</li>
 * </ul>
 *
 * <p><strong>Operational warning:</strong> reordering variants in this list changes
 * which users land on which variant (because cumulative ranges shift). Once an
 * experiment is running, prefer setting an unwanted variant's {@code weight}
 * to {@code 0} rather than removing or reordering entries.</p>
 *
 * @param name     human-readable rule name; non-blank
 * @param variants the ordered list of weighted variants whose weights must sum to 100
 */
public record MultiVariantRule(String name, List<WeightedVariant> variants) implements Rule {

    /** Maximum number of variants per rule (sanity bound to catch malformed configs). */
    public static final int MAX_VARIANTS = 50;

    /**
     * Compact constructor that validates fields and freezes the variants list.
     *
     * @throws NullPointerException     if any field is null
     * @throws IllegalArgumentException if {@code name} is blank, {@code variants} is empty,
     *                                  exceeds {@link #MAX_VARIANTS}, or weights do not sum to 100
     */
    public MultiVariantRule {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(variants, "variants must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (variants.isEmpty()) {
            throw new IllegalArgumentException("variants must not be empty");
        }
        if (variants.size() > MAX_VARIANTS) {
            throw new IllegalArgumentException(
                    "variants size " + variants.size() + " exceeds maximum " + MAX_VARIANTS);
        }
        int sum = 0;
        for (WeightedVariant v : variants) {
            sum += v.weight();
        }
        if (sum != 100) {
            throw new IllegalArgumentException(
                    "variant weights must sum to 100, got " + sum);
        }
        variants = List.copyOf(variants);
    }
}
