package com.openflags.core.evaluation.rule;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class BucketAllocatorTest {

    @Test
    void bucket_isDeterministic() {
        String flagKey = "my-flag";
        for (int i = 0; i < 10_000; i++) {
            String tk = "user-" + i;
            int first = BucketAllocator.bucket(flagKey, tk);
            for (int j = 0; j < 5; j++) {
                assertThat(BucketAllocator.bucket(flagKey, tk)).isEqualTo(first);
            }
        }
    }

    @Test
    void bucket_alwaysInRange() {
        String flagKey = "test-flag";
        for (int i = 0; i < 10_000; i++) {
            int b = BucketAllocator.bucket(flagKey, UUID.randomUUID().toString());
            assertThat(b).isBetween(0, 99);
        }
    }

    @Test
    void bucket_distribution_isUniform() {
        String flagKey = "test-flag";
        long total = 100_000;
        long lower = 0; // buckets [0,49]
        for (int i = 0; i < total; i++) {
            int b = BucketAllocator.bucket(flagKey, UUID.randomUUID().toString());
            if (b < 50) lower++;
        }
        long upper = total - lower;
        assertThat(lower).isBetween(49_000L, 51_000L);
        assertThat(upper).isBetween(49_000L, 51_000L);
    }

    @Test
    void bucket_throwsWhenFlagKeyNull() {
        assertThatThrownBy(() -> BucketAllocator.bucket(null, "user-1"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void bucket_throwsWhenTargetingKeyNull() {
        assertThatThrownBy(() -> BucketAllocator.bucket("flag", null))
                .isInstanceOf(NullPointerException.class);
    }
}
