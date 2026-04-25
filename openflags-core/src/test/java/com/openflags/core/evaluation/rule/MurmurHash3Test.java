package com.openflags.core.evaluation.rule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MurmurHash3Test {

    @Test
    void hash32_emptyString() {
        assertThat(MurmurHash3.hash32("")).isEqualTo(0x00000000);
    }

    @Test
    void hash32_singleChar() {
        assertThat(MurmurHash3.hash32("a")).isEqualTo(0x3c2569b2);
    }

    @Test
    void hash32_threeChars() {
        assertThat(MurmurHash3.hash32("abc")).isEqualTo(0xb3dd93fa);
    }

    @Test
    void hash32_fiveChars() {
        assertThat(MurmurHash3.hash32("hello")).isEqualTo(0x248bfa47);
    }

    @Test
    void hash32_longString() {
        assertThat(MurmurHash3.hash32("The quick brown fox jumps over the lazy dog"))
                .isEqualTo(0x2e4ff723);
    }

    @Test
    void hash32_isDeterministic() {
        String input = "test-flag:user-abc";
        int first = MurmurHash3.hash32(input);
        for (int i = 0; i < 100; i++) {
            assertThat(MurmurHash3.hash32(input)).isEqualTo(first);
        }
    }
}
