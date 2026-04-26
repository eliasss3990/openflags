package com.openflags.core.evaluation.rule;

import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Statistical distribution test for {@link VariantSelector} combined with {@link BucketAllocator}.
 * <p>
 * Runs 10 000 synthetic targeting keys through the full bucket + select pipeline and
 * asserts that each variant lands within ±2 % of its declared weight.
 * </p>
 */
class VariantDistributionTest {

    private static final FlagValue A = FlagValue.of("A", FlagType.STRING);
    private static final FlagValue B = FlagValue.of("B", FlagType.STRING);
    private static final FlagValue C = FlagValue.of("C", FlagType.STRING);

    private static final List<WeightedVariant> VARIANTS = List.of(
            new WeightedVariant(A, 33),
            new WeightedVariant(B, 33),
            new WeightedVariant(C, 34)
    );

    private static final int SAMPLE_SIZE = 10_000;
    private static final double TOLERANCE = 0.02; // ±2 %

    @Test
    void distributionIsWithinTolerance() {
        Map<String, Integer> counts = new HashMap<>(Map.of("A", 0, "B", 0, "C", 0));

        for (int i = 0; i < SAMPLE_SIZE; i++) {
            String targetingKey = "user-" + i;
            int bucket = BucketAllocator.bucket("flag-dist-test", targetingKey);
            WeightedVariant selected = VariantSelector.select(VARIANTS, bucket);
            String label = selected.value().asString();
            counts.merge(label, 1, Integer::sum);
        }

        assertVariantInTolerance(counts, "A", 33);
        assertVariantInTolerance(counts, "B", 33);
        assertVariantInTolerance(counts, "C", 34);
    }

    private void assertVariantInTolerance(Map<String, Integer> counts, String variant, int expectedWeight) {
        double expectedRate = expectedWeight / 100.0;
        double actualRate   = counts.get(variant) / (double) SAMPLE_SIZE;
        double delta        = Math.abs(actualRate - expectedRate);

        assertThat(delta)
                .as("variant %s: expected ~%.0f%%, got %.2f%% (delta=%.4f, tolerance=%.2f)",
                        variant, expectedRate * 100, actualRate * 100, delta, TOLERANCE)
                .isLessThanOrEqualTo(TOLERANCE);
    }
}
