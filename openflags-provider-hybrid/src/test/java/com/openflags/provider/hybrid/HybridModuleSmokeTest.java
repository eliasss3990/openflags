package com.openflags.provider.hybrid;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test that verifies the module compiles and basic types are accessible.
 */
class HybridModuleSmokeTest {

    @Test
    void snapshotFormatValuesExist() {
        assertThat(SnapshotFormat.values()).contains(SnapshotFormat.YAML, SnapshotFormat.JSON);
    }
}
