package com.openflags.core.evaluation.rule;

import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WeightedVariantTest {

    private static final FlagValue BOOL_TRUE = FlagValue.of(true, FlagType.BOOLEAN);

    @Test
    void validVariantStoresFields() {
        WeightedVariant v = new WeightedVariant(BOOL_TRUE, 50);
        assertThat(v.value()).isEqualTo(BOOL_TRUE);
        assertThat(v.weight()).isEqualTo(50);
    }

    @Test
    void weightZeroIsAllowed() {
        WeightedVariant v = new WeightedVariant(BOOL_TRUE, 0);
        assertThat(v.weight()).isZero();
    }

    @Test
    void weightHundredIsAllowed() {
        WeightedVariant v = new WeightedVariant(BOOL_TRUE, 100);
        assertThat(v.weight()).isEqualTo(100);
    }

    @Test
    void negativeWeightThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WeightedVariant(BOOL_TRUE, -1))
                .withMessageContaining("-1");
    }

    @Test
    void weightAboveHundredThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WeightedVariant(BOOL_TRUE, 101))
                .withMessageContaining("101");
    }

    @Test
    void nullValueThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new WeightedVariant(null, 50));
    }
}
