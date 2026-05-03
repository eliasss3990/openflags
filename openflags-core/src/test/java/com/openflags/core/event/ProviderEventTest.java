package com.openflags.core.event;

import com.openflags.core.provider.ProviderState;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SuppressWarnings("deprecation") // ProviderState.STALE referenced in toString test (ADR-6)
class ProviderEventTest {

    @Test
    void constructor_storesStateAndMessage() {
        ProviderEvent event = new ProviderEvent(ProviderState.READY, Optional.of("initialized"));
        assertThat(event.state()).isEqualTo(ProviderState.READY);
        assertThat(event.message()).contains("initialized");
    }

    @Test
    void constructor_emptyMessage() {
        ProviderEvent event = new ProviderEvent(ProviderState.ERROR, Optional.empty());
        assertThat(event.state()).isEqualTo(ProviderState.ERROR);
        assertThat(event.message()).isEmpty();
    }

    @Test
    void equalsAndHashCode_workCorrectly() {
        ProviderEvent e1 = new ProviderEvent(ProviderState.READY, Optional.of("msg"));
        ProviderEvent e2 = new ProviderEvent(ProviderState.READY, Optional.of("msg"));
        ProviderEvent e3 = new ProviderEvent(ProviderState.ERROR, Optional.of("msg"));
        assertThat(e1).isEqualTo(e2).hasSameHashCodeAs(e2);
        assertThat(e1).isNotEqualTo(e3);
    }

    @Test
    void toString_containsStateAndMessage() {
        ProviderEvent event = new ProviderEvent(ProviderState.STALE, Optional.of("stale data"));
        assertThat(event.toString()).contains("STALE").contains("stale data");
    }
}
