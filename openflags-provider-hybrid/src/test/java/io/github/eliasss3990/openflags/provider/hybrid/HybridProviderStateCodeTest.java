package io.github.eliasss3990.openflags.provider.hybrid;

import io.github.eliasss3990.openflags.core.provider.ProviderState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test: la mapping de {@link HybridFlagProvider#providerStateCode}
 * debe coincidir con la authoritative en
 * {@code io.github.eliasss3990.openflags.core.metrics.MicrometerMetricsRecorder#providerStateCode}.
 *
 * <p>Si se agrega un nuevo {@link ProviderState}, este test falla salvo que
 * se actualicen ambas copias. El código 4 está permanentemente reservado
 * (era STALE, removido en 2.0 via ADR-6).
 */
class HybridProviderStateCodeTest {

    @Test
    void providerStateCode_matchesAuthoritativeMapping() {
        Map<ProviderState, Integer> expected = Map.of(
                ProviderState.NOT_READY, 0,
                ProviderState.READY, 1,
                ProviderState.DEGRADED, 2,
                ProviderState.ERROR, 3,
                // code 4 reservado (STALE removido en 2.0)
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
        // Defensa: garantiza que todos los estados existentes están mapeados
        // (si se agrega uno nuevo sin actualizar el switch, el compilador grita;
        // este test detecta el caso opuesto: estado existente sin tests).
        for (ProviderState state : ProviderState.values()) {
            int code = HybridFlagProvider.providerStateCode(state);
            assertThat(code)
                    .as("code for %s must not be -1 (unknown)", state)
                    .isNotEqualTo(-1);
        }
    }
}
