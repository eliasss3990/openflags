package com.openflags.core.evaluation.rule;

import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MultiVariantRuleTest {

    private static final FlagValue A = FlagValue.of("a", FlagType.STRING);
    private static final FlagValue B = FlagValue.of("b", FlagType.STRING);
    private static final FlagValue C = FlagValue.of("c", FlagType.STRING);

    @Test
    void singleVariantWeight100() {
        MultiVariantRule rule = new MultiVariantRule("r", List.of(new WeightedVariant(A, 100)));
        assertThat(rule.variants()).hasSize(1);
        assertThat(rule.name()).isEqualTo("r");
    }

    @Test
    void twoVariantsHalfHalf() {
        MultiVariantRule rule = new MultiVariantRule("r", List.of(
                new WeightedVariant(A, 50),
                new WeightedVariant(B, 50)));
        assertThat(rule.variants()).hasSize(2);
    }

    @Test
    void threeVariants30_40_30() {
        MultiVariantRule rule = new MultiVariantRule("r", List.of(
                new WeightedVariant(A, 30),
                new WeightedVariant(B, 40),
                new WeightedVariant(C, 30)));
        assertThat(rule.variants()).hasSize(3);
    }

    @Test
    void variantsListIsImmutable() {
        List<WeightedVariant> mutable = new ArrayList<>();
        mutable.add(new WeightedVariant(A, 100));
        MultiVariantRule rule = new MultiVariantRule("r", mutable);
        assertThat(rule.variants()).isUnmodifiable();
    }

    @Test
    void weightsSum99Throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MultiVariantRule("r", List.of(
                        new WeightedVariant(A, 50),
                        new WeightedVariant(B, 49))))
                .withMessageContaining("99");
    }

    @Test
    void weightsSum101Throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MultiVariantRule("r", List.of(
                        new WeightedVariant(A, 51),
                        new WeightedVariant(B, 50))))
                .withMessageContaining("101");
    }

    @Test
    void emptyVariantsThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MultiVariantRule("r", Collections.emptyList()));
    }

    @Test
    void tooManyVariantsThrows() {
        List<WeightedVariant> variants = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            variants.add(new WeightedVariant(A, 0));
        }
        // sum is 0, but size check happens first
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MultiVariantRule("r", variants))
                .withMessageContaining("51");
    }

    @Test
    void blankNameThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MultiVariantRule("  ", List.of(new WeightedVariant(A, 100))));
    }
}
