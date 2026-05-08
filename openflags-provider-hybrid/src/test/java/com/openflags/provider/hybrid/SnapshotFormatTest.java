package com.openflags.provider.hybrid;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link SnapshotFormat} exposes its expected enum constants.
 */
class SnapshotFormatTest {

    @Test
    void snapshotFormatValuesExist() {
        assertThat(SnapshotFormat.values()).contains(SnapshotFormat.YAML, SnapshotFormat.JSON);
    }
}
