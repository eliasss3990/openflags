package io.github.eliasss3990.openflags.core;

import io.github.eliasss3990.openflags.core.metrics.MicrometerMetricsRecorder;
import io.github.eliasss3990.openflags.core.provider.FlagProvider;
import io.github.eliasss3990.openflags.core.provider.ProviderState;
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
    void metricsRecorder_micrometer_recordsIntoInjectedRegistry() {
        // End-to-end: the recorder passed via .metricsRecorder() must be the one
        // the client actually uses; the counter must materialize in the same
        // injected registry (not a parallel one). metricsTagFlagKey=false here
        // must also be honored.
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        doNothing().when(provider).init();
        when(provider.getFlag("k")).thenReturn(java.util.Optional.empty());

        OpenFlagsClient client = OpenFlagsClient.builder()
                .provider(provider)
                .providerType("file")
                .metricsRecorder(new MicrometerMetricsRecorder(registry, false))
                .build();
        try {
            // Precondition: the counter does not yet exist in the injected registry.
            assertThat(registry.find("openflags.evaluations.total").counter())
                    .as("registry empty before any evaluation")
                    .isNull();

            client.getBooleanValue("k", true);

            Counter c = registry.find("openflags.evaluations.total").counter();
            assertThat(c)
                    .as("counter must be registered in the injected registry")
                    .isNotNull();
            assertThat(c.count())
                    .as("a single evaluation must increment the counter once")
                    .isEqualTo(1.0);
            assertThat(c.getId().getTag("flag"))
                    .as("MicrometerMetricsRecorder built with tagFlagKey=false must not attach the flag tag")
                    .isNull();
        } finally {
            client.shutdown();
        }
    }
}
