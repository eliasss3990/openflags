package com.openflags.core;

import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderState;
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
}
