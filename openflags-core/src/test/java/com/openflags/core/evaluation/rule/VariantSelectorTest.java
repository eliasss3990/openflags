package com.openflags.core.evaluation.rule;

import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class VariantSelectorTest {

    private static final FlagValue A = FlagValue.of("A", FlagType.STRING);
    private static final FlagValue B = FlagValue.of("B", FlagType.STRING);
    private static final FlagValue C = FlagValue.of("C", FlagType.STRING);
    private static final FlagValue X = FlagValue.of("X", FlagType.STRING);

    private static final WeightedVariant VA30 = new WeightedVariant(A, 30);
    private static final WeightedVariant VB50 = new WeightedVariant(B, 50);
    private static final WeightedVariant VC20 = new WeightedVariant(C, 20);

    private static final List<WeightedVariant> ABC = List.of(VA30, VB50, VC20);

    @Test
    void bucket0selectsA() {
        assertThat(VariantSelector.select(ABC, 0).value()).isEqualTo(A);
    }

    @Test
    void bucket29selectsA() {
        assertThat(VariantSelector.select(ABC, 29).value()).isEqualTo(A);
    }

    @Test
    void bucket30selectsB() {
        assertThat(VariantSelector.select(ABC, 30).value()).isEqualTo(B);
    }

    @Test
    void bucket79selectsB() {
        assertThat(VariantSelector.select(ABC, 79).value()).isEqualTo(B);
    }

    @Test
    void bucket80selectsC() {
        assertThat(VariantSelector.select(ABC, 80).value()).isEqualTo(C);
    }

    @Test
    void bucket99selectsC() {
        assertThat(VariantSelector.select(ABC, 99).value()).isEqualTo(C);
    }

    @Test
    void singleVariantAlwaysSelected() {
        List<WeightedVariant> single = List.of(new WeightedVariant(X, 100));
        for (int b = 0; b <= 99; b++) {
            assertThat(VariantSelector.select(single, b).value()).isEqualTo(X);
        }
    }

    @Test
    void weightZeroVariantNeverSelected() {
        WeightedVariant zero = new WeightedVariant(C, 0);
        WeightedVariant full = new WeightedVariant(A, 100);
        List<WeightedVariant> variants = List.of(full, zero);
        for (int b = 0; b <= 99; b++) {
            assertThat(VariantSelector.select(variants, b).value()).isEqualTo(A);
        }
    }

    @Test
    void negativeBucketThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> VariantSelector.select(ABC, -1));
    }

    @Test
    void bucket100Throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> VariantSelector.select(ABC, 100));
    }
}
