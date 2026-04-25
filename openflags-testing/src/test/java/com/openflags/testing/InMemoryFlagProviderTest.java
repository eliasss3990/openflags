package com.openflags.testing;

import com.openflags.core.event.ChangeType;
import com.openflags.core.event.FlagChangeEvent;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.model.FlagType;
import com.openflags.core.provider.ProviderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class InMemoryFlagProviderTest {

    private InMemoryFlagProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InMemoryFlagProvider();
        provider.init();
    }

    @Test
    void init_isIdempotent() {
        provider.init();
        assertThat(provider.getState()).isEqualTo(ProviderState.READY);
    }

    @Test
    void setBoolean_andGet() {
        provider.setBoolean("dark-mode", true);
        assertThat(provider.getFlag("dark-mode")).isPresent();
        assertThat(provider.getFlag("dark-mode").get().type()).isEqualTo(FlagType.BOOLEAN);
        assertThat(provider.getFlag("dark-mode").get().value().asBoolean()).isTrue();
    }

    @Test
    void setString_andGet() {
        provider.setString("theme", "dark");
        assertThat(provider.getFlag("theme").get().value().asString()).isEqualTo("dark");
    }

    @Test
    void setNumber_andGet() {
        provider.setNumber("rate", 0.5);
        assertThat(provider.getFlag("rate").get().value().asNumber()).isEqualTo(0.5);
    }

    @Test
    void setObject_andGet() {
        provider.setObject("config", Map.of("timeout", 30));
        assertThat(provider.getFlag("config").get().value().asObject()).containsEntry("timeout", 30);
    }

    @Test
    void setDisabled_makesDisabled() {
        provider.setBoolean("feature", true);
        provider.setDisabled("feature");
        assertThat(provider.getFlag("feature").get().enabled()).isFalse();
    }

    @Test
    void setDisabled_throwsWhenFlagNotFound() {
        assertThatThrownBy(() -> provider.setDisabled("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remove_removesFlag() {
        provider.setBoolean("flag", true);
        provider.remove("flag");
        assertThat(provider.getFlag("flag")).isEmpty();
    }

    @Test
    void clear_removesAllFlags() {
        provider.setBoolean("a", true).setString("b", "x");
        provider.clear();
        assertThat(provider.getAllFlags()).isEmpty();
    }

    @Test
    void chaining_works() {
        provider.setBoolean("a", true).setString("b", "x").setNumber("c", 1.0);
        assertThat(provider.getAllFlags()).containsKeys("a", "b", "c");
    }

    @Test
    void changeListeners_receivedOnSet() {
        List<FlagChangeEvent> events = new ArrayList<>();
        provider.addChangeListener(events::add);
        provider.setBoolean("f", true);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).changeType()).isEqualTo(ChangeType.CREATED);
    }

    @Test
    void changeListeners_receivedOnUpdate() {
        provider.setBoolean("f", false);
        List<FlagChangeEvent> events = new ArrayList<>();
        provider.addChangeListener(events::add);
        provider.setBoolean("f", true);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).changeType()).isEqualTo(ChangeType.UPDATED);
    }

    @Test
    void changeListeners_receivedOnRemove() {
        provider.setBoolean("f", true);
        List<FlagChangeEvent> events = new ArrayList<>();
        provider.addChangeListener(events::add);
        provider.remove("f");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).changeType()).isEqualTo(ChangeType.DELETED);
    }

    @Test
    void removeChangeListener_stopsReceivingEvents() {
        List<FlagChangeEvent> events = new ArrayList<>();
        FlagChangeListener listener = events::add;
        provider.addChangeListener(listener);
        provider.removeChangeListener(listener);
        provider.setBoolean("flag", true);
        assertThat(events).isEmpty();
    }

    @Test
    void removeChangeListener_withDifferentInstance_doesNothing() {
        List<FlagChangeEvent> events = new ArrayList<>();
        provider.addChangeListener(events::add);
        provider.removeChangeListener(e -> {});
        provider.setBoolean("flag-neg", true);
        assertThat(events).hasSize(1);
    }

    @Test
    void shutdown_isIdempotent() {
        provider.shutdown();
        assertThatCode(provider::shutdown).doesNotThrowAnyException();
    }

    @Test
    void evaluationAfterShutdown_throwsIllegalState() {
        provider.shutdown();
        assertThatThrownBy(() -> provider.getFlag("any"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(provider::getAllFlags)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void concurrentWrites_areThreadSafe() throws InterruptedException {
        int threadCount = 10;
        int flagsPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < flagsPerThread; i++) {
                        provider.setBoolean("flag-" + threadId + "-" + i, true);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(provider.getAllFlags()).hasSize(threadCount * flagsPerThread);
    }
}
