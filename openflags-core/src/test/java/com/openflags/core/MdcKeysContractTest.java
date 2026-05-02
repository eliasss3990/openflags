package com.openflags.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the public string values of {@link OpenFlagsMdc}. Logback/Log4j
 * patterns referencing these keys (e.g. {@code %X{openflags.flag_key}})
 * would break on rename, so any change is a major version bump.
 */
class MdcKeysContractTest {

    @Test
    void mdcKeys_haveStableValues() {
        assertThat(OpenFlagsMdc.FLAG_KEY).isEqualTo("openflags.flag_key");
        assertThat(OpenFlagsMdc.TARGETING_KEY).isEqualTo("openflags.targeting_key");
    }
}
