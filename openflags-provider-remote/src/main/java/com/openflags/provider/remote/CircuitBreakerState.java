package com.openflags.provider.remote;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight failure-counting circuit breaker for {@link RemoteFlagProvider}.
 *
 * <p>
 * Tracks the number of consecutive poll failures and computes the next poll
 * delay using exponential backoff once a configurable failure threshold is
 * exceeded. The next scheduled poll itself acts as the half-open probe:
 * a single successful poll resets the failure counter back to zero.
 * </p>
 *
 * <p>
 * Threading model: this class is thread-safe by composition (the counter is
 * an {@link AtomicInteger}). It is intended to be driven by a single-threaded
 * scheduler so that {@link #recordFailure()}, {@link #recordSuccess()} and
 * {@link #nextDelay()} cannot interleave in surprising ways.
 * </p>
 *
 * <p>
 * See ADR-504 for the rationale behind the simple-counter design (vs.
 * a half-open / probe / sliding window model).
 * </p>
 */
final class CircuitBreakerState {

    /** Hard cap on the bit-shift used in the backoff calculation. */
    static final int MAX_OVERFLOW = 30;

    private final int failureThreshold;
    private final Duration baseInterval;
    private final Duration maxBackoff;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    CircuitBreakerState(int failureThreshold, Duration baseInterval, Duration maxBackoff) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be > 0");
        }
        if (baseInterval == null || baseInterval.isNegative() || baseInterval.isZero()) {
            throw new IllegalArgumentException("baseInterval must be positive");
        }
        if (maxBackoff == null || maxBackoff.isNegative() || maxBackoff.isZero()) {
            throw new IllegalArgumentException("maxBackoff must be positive");
        }
        if (maxBackoff.compareTo(baseInterval) < 0) {
            throw new IllegalArgumentException(
                    "maxBackoff must be >= baseInterval");
        }
        this.failureThreshold = failureThreshold;
        this.baseInterval = baseInterval;
        this.maxBackoff = maxBackoff;
    }

    /** Increments the consecutive-failures counter. */
    void recordFailure() {
        consecutiveFailures.incrementAndGet();
    }

    /** Resets the consecutive-failures counter to zero. */
    void recordSuccess() {
        consecutiveFailures.set(0);
    }

    /** Returns the current consecutive-failures count. */
    int failureCount() {
        return consecutiveFailures.get();
    }

    /**
     * Returns {@code true} once {@link #failureCount()} has reached the
     * configured threshold. The breaker remains open until the next
     * successful poll calls {@link #recordSuccess()}.
     */
    boolean isOpen() {
        return consecutiveFailures.get() >= failureThreshold;
    }

    /**
     * Computes the delay until the next scheduled poll.
     *
     * <p>
     * Below the threshold the delay equals {@code baseInterval}. Once at or
     * above the threshold the delay grows as {@code baseInterval * 2^n},
     * where {@code n} is the number of failures past the threshold, and is
     * capped at {@code maxBackoff}. Bit-shifts are clamped to
     * {@link #MAX_OVERFLOW} to avoid arithmetic overflow.
     * </p>
     */
    Duration nextDelay() {
        int failures = consecutiveFailures.get();
        if (failures < failureThreshold) {
            return baseInterval;
        }
        int overflow = Math.min(failures - failureThreshold, MAX_OVERFLOW);
        long shift = 1L << overflow;
        long baseMillis = baseInterval.toMillis();
        long maxMillis = maxBackoff.toMillis();
        // Saturating multiply: if baseMillis * shift would exceed maxMillis (or overflow long),
        // we are already past the cap, so short-circuit to maxMillis.
        long candidate = (baseMillis > maxMillis / shift) ? maxMillis : baseMillis * shift;
        return Duration.ofMillis(Math.min(candidate, maxMillis));
    }
}
