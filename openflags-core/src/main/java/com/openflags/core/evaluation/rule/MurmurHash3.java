package com.openflags.core.evaluation.rule;

import java.nio.charset.StandardCharsets;

/**
 * MurmurHash3 32-bit implementation, package-private.
 * <p>
 * Self-contained, no dependencies. Used by {@link BucketAllocator} for consistent
 * hashing of flag rollouts. Not intended for cryptographic use.
 * </p>
 */
final class MurmurHash3 {

    private MurmurHash3() {}

    /**
     * Computes the 32-bit MurmurHash3 of the given UTF-8 encoded string.
     *
     * @param input the string to hash; must not be null
     * @return the 32-bit hash
     */
    static int hash32(String input) {
        byte[] data = input.getBytes(StandardCharsets.UTF_8);
        int length = data.length;
        int h = 0; // seed = 0

        int nblocks = length >> 2;
        for (int i = 0; i < nblocks; i++) {
            int offset = i << 2;
            int k = (data[offset] & 0xff)
                    | ((data[offset + 1] & 0xff) << 8)
                    | ((data[offset + 2] & 0xff) << 16)
                    | ((data[offset + 3] & 0xff) << 24);

            k *= 0xcc9e2d51;
            k = Integer.rotateLeft(k, 15);
            k *= 0x1b873593;

            h ^= k;
            h = Integer.rotateLeft(h, 13);
            h = h * 5 + 0xe6546b64;
        }

        // tail
        int tail = nblocks << 2;
        int k1 = 0;
        switch (length & 3) {
            case 3:
                k1 ^= (data[tail + 2] & 0xff) << 16;
                // fall through
            case 2:
                k1 ^= (data[tail + 1] & 0xff) << 8;
                // fall through
            case 1:
                k1 ^= (data[tail] & 0xff);
                k1 *= 0xcc9e2d51;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= 0x1b873593;
                h ^= k1;
                break;
            default:
                break;
        }

        // finalization
        h ^= length;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;

        return h;
    }
}
