package com.openflags.core;

import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenFlagsClientBuilderTest {

    @Mock
    private FlagProvider provider;

    @Test
    void build_callsInitOnProvider() {
        doNothing().when(provider).init();
        OpenFlagsClient client = OpenFlagsClient.builder().provider(provider).build();
        verify(provider).init();
        client.shutdown();
    }

    @Test
    void build_throwsWhenNoProviderSet() {
        assertThatThrownBy(() -> OpenFlagsClient.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FlagProvider");
    }

    @Test
    void provider_throwsWhenNull() {
        assertThatThrownBy(() -> OpenFlagsClient.builder().provider(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void build_returnsReadyClient() {
        doNothing().when(provider).init();
        when(provider.getState()).thenReturn(ProviderState.READY);
        OpenFlagsClient client = OpenFlagsClient.builder().provider(provider).build();
        assertThat(client.getProviderState()).isEqualTo(ProviderState.READY);
        client.shutdown();
    }

    @Test
    @SuppressWarnings("deprecation") // exercising the deprecated reflective entry point on purpose (ADR-4)
    void metricsRegistry_thenMetricsTagFlagKeyFalse_isHonored() {
        // Regression: previously the recorder was built eagerly inside
        // metricsRegistry(...), so a later metricsTagFlagKey(false) was
        // silently ignored. Resolution must defer to build().
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        doNothing().when(provider).init();
        when(provider.getFlag("k")).thenReturn(java.util.Optional.empty());

        OpenFlagsClient client = OpenFlagsClient.builder()
                .provider(provider)
                .providerType("file")
                .metricsRegistry(registry)
                .metricsTagFlagKey(false)
                .build();
        try {
            client.getBooleanValue("k", true);
            Counter c = registry.find("openflags.evaluations.total").counter();
            assertThat(c).isNotNull();
            assertThat(c.getId().getTag("flag"))
                    .as("metricsTagFlagKey(false) called after metricsRegistry must be honored")
                    .isNull();
        } finally {
            client.shutdown();
        }
    }
}
