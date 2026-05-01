package com.openflags.provider.remote;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerStateTest {

    private static final Duration BASE = Duration.ofSeconds(30);
    private static final Duration MAX = Duration.ofMinutes(5);
    private static final int THRESHOLD = 5;

    @Test
    void newBreakerIsClosedWithBaseDelay() {
        CircuitBreakerState cb = newBreaker();
        assertThat(cb.isOpen()).isFalse();
        assertThat(cb.failureCount()).isZero();
        assertThat(cb.nextDelay()).isEqualTo(BASE);
    }

    @Test
    void belowThresholdKeepsBaseDelay() {
        CircuitBreakerState cb = newBreaker();
        for (int i = 0; i < THRESHOLD - 1; i++) {
            cb.recordFailure();
            assertThat(cb.isOpen()).isFalse();
            assertThat(cb.nextDelay()).isEqualTo(BASE);
        }
    }

    @Test
    void atThresholdOpensCircuitAndStartsBackoff() {
        CircuitBreakerState cb = newBreaker();
        for (int i = 0; i < THRESHOLD; i++)
            cb.recordFailure();
        assertThat(cb.isOpen()).isTrue();
        // overflow=0 → BASE * 2^0 = BASE
        assertThat(cb.nextDelay()).isEqualTo(BASE);
    }

    @Test
    void exponentialGrowthAndCap() {
        CircuitBreakerState cb = newBreaker();
        for (int i = 0; i < THRESHOLD; i++)
            cb.recordFailure();
        // 5 failures → 30s; 6 → 60s; 7 → 120s; 8 → 240s; 9 → 480s capped to 300s
        assertThat(cb.nextDelay()).isEqualTo(Duration.ofSeconds(30));
        cb.recordFailure();
        assertThat(cb.nextDelay()).isEqualTo(Duration.ofSeconds(60));
        cb.recordFailure();
        assertThat(cb.nextDelay()).isEqualTo(Duration.ofSeconds(120));
        cb.recordFailure();
        assertThat(cb.nextDelay()).isEqualTo(Duration.ofSeconds(240));
        cb.recordFailure();
        assertThat(cb.nextDelay()).isEqualTo(MAX);
        cb.recordFailure();
        assertThat(cb.nextDelay()).isEqualTo(MAX);
    }

    @Test
    void recordSuccessResetsCounterAndCloses() {
        CircuitBreakerState cb = newBreaker();
        for (int i = 0; i < 10; i++)
            cb.recordFailure();
        assertThat(cb.isOpen()).isTrue();

        cb.recordSuccess();

        assertThat(cb.failureCount()).isZero();
        assertThat(cb.isOpen()).isFalse();
        assertThat(cb.nextDelay()).isEqualTo(BASE);
    }

    @Test
    void manyFailuresDoNotOverflow() {
        CircuitBreakerState cb = newBreaker();
        for (int i = 0; i < 100; i++)
            cb.recordFailure();

        assertThat(cb.failureCount()).isEqualTo(100);
        assertThat(cb.isOpen()).isTrue();
        assertThat(cb.nextDelay())
                .isLessThanOrEqualTo(MAX)
                .isPositive();
    }

    @Test
    void saturatesOnLargeBaseIntervalWithoutOverflow() {
        Duration largeBase = Duration.ofHours(1);
        Duration largeMax = Duration.ofHours(24);
        CircuitBreakerState cb = new CircuitBreakerState(2, largeBase, largeMax);
        for (int i = 0; i < 200; i++)
            cb.recordFailure();
        Duration delay = cb.nextDelay();
        assertThat(delay).isEqualTo(largeMax);
    }

    @Test
    void rejectsInvalidThreshold() {
        assertThatThrownBy(() -> new CircuitBreakerState(0, BASE, MAX))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreakerState(-1, BASE, MAX))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveDurations() {
        assertThatThrownBy(() -> new CircuitBreakerState(5, Duration.ZERO, MAX))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreakerState(5, BASE, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreakerState(5, null, MAX))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreakerState(5, BASE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMaxBackoffSmallerThanBase() {
        assertThatThrownBy(() -> new CircuitBreakerState(5, Duration.ofMinutes(5), Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBackoff");
    }

    private static CircuitBreakerState newBreaker() {
        return new CircuitBreakerState(THRESHOLD, BASE, MAX);
    }
}
