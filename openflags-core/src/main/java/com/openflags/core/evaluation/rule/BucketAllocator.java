package com.openflags.core.evaluation.rule;

import java.util.Objects;

/**
 * Maps a {@code (flagKey, targetingKey)} pair to a bucket in the closed range {@code [0, 99]}.
 * <p>
 * Deterministic and stateless: the same input always produces the same bucket.
 * Uses MurmurHash3 (32-bit) under the hood.
 * </p>
 * <p>
 * Including the {@code flagKey} in the hash input acts as a per-flag salt: two flags
 * with the same rollout percentage will not necessarily place the same user in the
 * same bucket, decoupling concurrent experiments.
 * </p>
 */
public final class BucketAllocator {

    private BucketAllocator() {}

    /**
     * Computes the bucket for the given {@code (flagKey, targetingKey)} pair.
     *
     * @param flagKey      the flag key; must not be null
     * @param targetingKey the targeting key; must not be null
     * @return a bucket in {@code [0, 99]}
     * @throws NullPointerException if any argument is null
     */
    public static int bucket(String flagKey, String targetingKey) {
        Objects.requireNonNull(flagKey, "flagKey must not be null");
        Objects.requireNonNull(targetingKey, "targetingKey must not be null");
        int h = MurmurHash3.hash32(flagKey + ":" + targetingKey);
        long unsigned = h & 0xFFFFFFFFL;
        return (int) (unsigned % 100);
    }
}
