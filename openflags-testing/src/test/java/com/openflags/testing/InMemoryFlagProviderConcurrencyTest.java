package com.openflags.testing;

import com.openflags.core.event.FlagChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryFlagProviderConcurrencyTest {

    private InMemoryFlagProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InMemoryFlagProvider();
        provider.init();
    }

    @Test
    void concurrentPutFlagsAllEventsEmitted() throws Exception {
        int threadCount = 10;
        AtomicInteger eventCount = new AtomicInteger(0);
        provider.addChangeListener(e -> eventCount.incrementAndGet());

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int id = t;
            pool.submit(() -> {
                ready.countDown();
                try {
                    ready.await();
                    provider.setBoolean("flag-" + id, true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(provider.getAllFlags()).hasSize(threadCount);
        assertThat(eventCount.get()).isEqualTo(threadCount);
    }

    @Test
    void concurrentSetDisabledAndSetBoolean() throws Exception {
        provider.setBoolean("flag-x", true);

        CyclicBarrier barrier = new CyclicBarrier(2);
        CopyOnWriteArrayList<String> errors = new CopyOnWriteArrayList<>();

        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                provider.setDisabled("flag-x");
            } catch (Exception e) {
                // IllegalArgumentException is valid if setBoolean removed it, ignore
                if (!(e instanceof IllegalArgumentException)) {
                    errors.add("t1: " + e.getMessage());
                }
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                provider.setBoolean("flag-x", false);
            } catch (Exception e) {
                errors.add("t2: " + e.getMessage());
            }
        });

        t1.start();
        t2.start();
        t1.join(5000);
        t2.join(5000);

        assertThat(errors).isEmpty();
        // flag-x must still exist (either disabled or updated to false)
        assertThat(provider.getFlag("flag-x")).isPresent();
    }
}
