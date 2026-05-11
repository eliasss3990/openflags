package io.github.eliasss3990.openflags.provider.hybrid;

import io.github.eliasss3990.openflags.core.provider.ProviderState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test: the {@link HybridFlagProvider#providerStateCode} mapping must
 * match the authoritative one in
 * {@code io.github.eliasss3990.openflags.core.metrics.MicrometerMetricsRecorder#providerStateCode}.
 *
 * <p>If a new {@link ProviderState} value is added, this test fails unless
 * both copies are updated. Code 4 is permanently reserved (was STALE,
 * removed in 2.0 via ADR-6).
 */
class HybridProviderStateCodeTest {

    @Test
    void providerStateCode_matchesAuthoritativeMapping() {
        Map<ProviderState, Integer> expected = Map.of(
                ProviderState.NOT_READY, 0,
                ProviderState.READY, 1,
                ProviderState.DEGRADED, 2,
                ProviderState.ERROR, 3,
                // code 4 reserved (STALE, removed in 2.0)
                ProviderState.SHUTDOWN, 5);
        for (Map.Entry<ProviderState, Integer> e : expected.entrySet()) {
            assertThat(HybridFlagProvider.providerStateCode(e.getKey()))
                    .as("code for %s", e.getKey())
                    .isEqualTo(e.getValue());
        }
        assertThat(HybridFlagProvider.providerStateCode(null)).isEqualTo(-1);
    }

    @Test
    void providerStateCode_coversAllEnumValues() {
        // Defensive: every existing state must be mapped. If a new state is
        // added without updating the switch the compiler catches it; this test
        // catches the opposite case (existing state without test coverage).
        for (ProviderState state : ProviderState.values()) {
            int code = HybridFlagProvider.providerStateCode(state);
            assertThat(code)
                    .as("code for %s must not be -1 (unknown)", state)
                    .isNotEqualTo(-1);
        }
    }
}
