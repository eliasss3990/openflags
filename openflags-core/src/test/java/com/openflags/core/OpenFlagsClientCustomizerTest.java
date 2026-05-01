package com.openflags.core;

import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenFlagsClientCustomizerTest {

    @Test
    void customizer_receives_builder_and_mutations_persist() {
        FlagProvider provider = mock(FlagProvider.class);
        when(provider.getState()).thenReturn(ProviderState.READY);

        List<OpenFlagsClientBuilder> seen = new ArrayList<>();
        OpenFlagsClientCustomizer customizer = builder -> {
            seen.add(builder);
            builder.provider(provider);
        };

        OpenFlagsClientBuilder builder = OpenFlagsClient.builder();
        customizer.customize(builder);
        OpenFlagsClient client = builder.build();

        try {
            assertThat(seen).hasSize(1).containsExactly(builder);
            assertThat(client.getProviderState()).isEqualTo(ProviderState.READY);
        } finally {
            client.shutdown();
        }
    }

    @Test
    void multiple_customizers_compose_in_order() {
        FlagProvider provider = mock(FlagProvider.class);
        when(provider.getState()).thenReturn(ProviderState.READY);

        List<String> trace = new ArrayList<>();
        OpenFlagsClientCustomizer first = b -> trace.add("first");
        OpenFlagsClientCustomizer second = b -> {
            trace.add("second");
            b.provider(provider);
        };

        OpenFlagsClientBuilder builder = OpenFlagsClient.builder();
        first.customize(builder);
        second.customize(builder);
        OpenFlagsClient client = builder.build();

        try {
            assertThat(trace).containsExactly("first", "second");
            assertThat(client.getProviderState()).isEqualTo(ProviderState.READY);
        } finally {
            client.shutdown();
        }
    }
}
