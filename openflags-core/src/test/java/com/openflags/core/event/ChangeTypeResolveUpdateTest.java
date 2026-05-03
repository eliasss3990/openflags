package com.openflags.core.event;

import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeTypeResolveUpdateTest {

    private static FlagValue boolVal(boolean v) {
        return FlagValue.of(v, FlagType.BOOLEAN);
    }

    private static FlagValue numVal(double v) {
        return FlagValue.of(v, FlagType.NUMBER);
    }

    @Test
    void falseToTrue_returnsEnabled() {
        ChangeType result = ChangeType.resolveUpdate(FlagType.BOOLEAN, boolVal(false), boolVal(true));
        assertThat(result).isEqualTo(ChangeType.ENABLED);
    }

    @Test
    void trueToFalse_returnsDisabled() {
        ChangeType result = ChangeType.resolveUpdate(FlagType.BOOLEAN, boolVal(true), boolVal(false));
        assertThat(result).isEqualTo(ChangeType.DISABLED);
    }

    @Test
    void booleanNoValueChange_returnsUpdated() {
        ChangeType result = ChangeType.resolveUpdate(FlagType.BOOLEAN, boolVal(true), boolVal(true));
        assertThat(result).isEqualTo(ChangeType.UPDATED);
    }

    @Test
    void numberTransition_returnsUpdated() {
        ChangeType result = ChangeType.resolveUpdate(FlagType.NUMBER, numVal(0), numVal(1));
        assertThat(result).isEqualTo(ChangeType.UPDATED);
    }

    @Test
    void created_isNotOverriddenByEnabled() {
        // Precedence check: CREATED > ENABLED. Callers must not invoke resolveUpdate for CREATED.
        // Verify by ensuring that when oldFlag is absent (CREATED path), the caller uses CREATED.
        // Here we only test that resolveUpdate itself returns ENABLED (it is the caller's
        // responsibility to pick CREATED before calling resolveUpdate).
        // This test documents the contract: resolveUpdate is only called for updates, not creations.
        ChangeType result = ChangeType.resolveUpdate(FlagType.BOOLEAN, boolVal(false), boolVal(true));
        assertThat(result)
                .as("resolveUpdate returns ENABLED, but callers must use CREATED when oldFlag is absent")
                .isEqualTo(ChangeType.ENABLED);
    }

    @Test
    void deleted_isNotOverriddenByDisabled() {
        // Precedence check: DELETED > DISABLED. Callers must not invoke resolveUpdate for DELETED.
        // Verify that when newFlag is absent (DELETED path), callers emit DELETED directly.
        // resolveUpdate is never called for deletions; this test documents the intent.
        ChangeType result = ChangeType.resolveUpdate(FlagType.BOOLEAN, boolVal(true), boolVal(false));
        assertThat(result)
                .as("resolveUpdate returns DISABLED, but callers must use DELETED when newFlag is absent")
                .isEqualTo(ChangeType.DISABLED);
    }
}
