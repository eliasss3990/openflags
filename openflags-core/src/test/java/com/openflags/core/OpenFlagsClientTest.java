package com.openflags.core;

import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationReason;
import com.openflags.core.evaluation.EvaluationResult;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenFlagsClientTest {

    @Mock
    private FlagProvider provider;

    private OpenFlagsClient client;

    @BeforeEach
    void setUp() {
        doNothing().when(provider).init();
        client = OpenFlagsClient.builder().provider(provider).build();
    }

    @AfterEach
    void tearDown() {
        client.shutdown();
    }

    @Test
    void getBooleanValue_resolvesFlag() {
        Flag flag = new Flag("dark-mode", FlagType.BOOLEAN, FlagValue.of(true, FlagType.BOOLEAN), true, null);
        when(provider.getFlag("dark-mode")).thenReturn(Optional.of(flag));

        assertThat(client.getBooleanValue("dark-mode", false)).isTrue();
    }

    @Test
    void getBooleanValue_returnsDefaultWhenNotFound() {
        when(provider.getFlag("missing")).thenReturn(Optional.empty());

        assertThat(client.getBooleanValue("missing", false)).isFalse();
        assertThat(client.getBooleanValue("missing", true)).isTrue();
    }

    @Test
    void getStringValue_resolvesFlag() {
        Flag flag = new Flag("theme", FlagType.STRING, FlagValue.of("dark", FlagType.STRING), true, null);
        when(provider.getFlag("theme")).thenReturn(Optional.of(flag));

        assertThat(client.getStringValue("theme", "light")).isEqualTo("dark");
    }

    @Test
    void getNumberValue_resolvesFlag() {
        Flag flag = new Flag("rate", FlagType.NUMBER, FlagValue.of(0.5, FlagType.NUMBER), true, null);
        when(provider.getFlag("rate")).thenReturn(Optional.of(flag));

        assertThat(client.getNumberValue("rate", 0.0)).isEqualTo(0.5);
    }

    @Test
    void getObjectValue_resolvesFlag() {
        Map<String, Object> cfg = Map.of("timeout", 30);
        Flag flag = new Flag("config", FlagType.OBJECT, FlagValue.of(cfg, FlagType.OBJECT), true, null);
        when(provider.getFlag("config")).thenReturn(Optional.of(flag));

        assertThat(client.getObjectValue("config", Map.of())).containsEntry("timeout", 30);
    }

    @Test
    void getBooleanResult_includesReason() {
        when(provider.getFlag("missing")).thenReturn(Optional.empty());

        EvaluationResult<Boolean> result = client.getBooleanResult("missing", false, EvaluationContext.empty());
        assertThat(result.reason()).isEqualTo(EvaluationReason.FLAG_NOT_FOUND);
        assertThat(result.value()).isFalse();
    }

    @Test
    void getProviderState_delegatesToProvider() {
        when(provider.getState()).thenReturn(ProviderState.READY);
        assertThat(client.getProviderState()).isEqualTo(ProviderState.READY);
    }

    @Test
    void addChangeListener_delegatesToProvider() {
        FlagChangeListener listener = event -> {};
        client.addChangeListener(listener);
        verify(provider).addChangeListener(listener);
    }

    @Test
    void shutdown_isIdempotent() {
        client.shutdown();
        client.shutdown();
        verify(provider, times(1)).shutdown();
    }

    @Test
    void evaluationAfterShutdown_throwsIllegalState() {
        client.shutdown();
        assertThatThrownBy(() -> client.getBooleanValue("any", false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client.getStringValue("any", "default"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client.getNumberValue("any", 0.0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client.getObjectValue("any", Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getBooleanValue_withContext() {
        Flag flag = new Flag("feature", FlagType.BOOLEAN, FlagValue.of(true, FlagType.BOOLEAN), true, null);
        when(provider.getFlag("feature")).thenReturn(Optional.of(flag));

        EvaluationContext ctx = EvaluationContext.of("user-123");
        assertThat(client.getBooleanValue("feature", false, ctx)).isTrue();
    }
}
