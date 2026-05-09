package io.github.eliasss3990.openflags.provider.hybrid;

import io.github.eliasss3990.openflags.core.provider.ProviderState;
import io.github.eliasss3990.openflags.provider.file.FileFlagProvider;
import io.github.eliasss3990.openflags.provider.remote.RemoteFlagProvider;
import io.github.eliasss3990.openflags.provider.remote.RemotePollListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR-3: snapshot writes execute on a dedicated executor (off the poller
 * thread), bursts of polls coalesce into a single write, user-supplied
 * executors are not shut down by the provider, and pending writes are drained
 * during {@link HybridFlagProvider#shutdown()}.
 */
class HybridSnapshotExecutorTest {

    /** Executor that queues tasks until {@link #runAll()} is called explicitly. */
    private static final class ManualExecutor implements java.util.concurrent.Executor {
        final Deque<Runnable> queue = new ArrayDeque<>();
        @Override
        public synchronized void execute(Runnable command) {
            queue.add(command);
        }
        synchronized int drainOnce() {
            int ran = 0;
            while (!queue.isEmpty()) {
                queue.poll().run();
                ran++;
            }
            return ran;
        }
    }

    private HybridFlagProvider buildProvider(RemoteFlagProvider remote,
            FileFlagProvider file,
            SnapshotWriter writer,
            Path dir,
            java.util.concurrent.Executor executor) {
        HybridProviderConfig cfg = new HybridProviderConfig(
                HybridFlagProviderTest.REMOTE_CFG,
                dir.resolve("snap.json"),
                SnapshotFormat.JSON,
                false,
                Duration.ofMillis(200),
                false);
        return new HybridFlagProvider(cfg, remote, file, writer, executor);
    }

    @Test
    void pollExecutesWriteOnSnapshotExecutorThread(@TempDir Path dir) throws Exception {
        RemoteFlagProvider mockRemote = mock(RemoteFlagProvider.class);
        FileFlagProvider mockFile = mock(FileFlagProvider.class);
        SnapshotWriter mockWriter = mock(SnapshotWriter.class);
        when(mockRemote.getState()).thenReturn(ProviderState.READY);
        when(mockFile.getState()).thenReturn(ProviderState.READY);

        AtomicReference<Thread> writeThread = new AtomicReference<>();
        CountDownLatch wrote = new CountDownLatch(1);

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-snapshot-writer"));
        try {
            ArgumentCaptor<RemotePollListener> pollCaptor = ArgumentCaptor.forClass(RemotePollListener.class);
            HybridFlagProvider provider = buildProvider(mockRemote, mockFile, mockWriter, dir, executor);
            provider.init();
            verify(mockRemote, atLeastOnce()).setPollListener(pollCaptor.capture());

            // Install the thread-capture stub *after* init() so the synchronous
            // baseline write on the init thread does not race with our latch.
            clearInvocations(mockWriter);
            org.mockito.Mockito.doAnswer(inv -> {
                writeThread.set(Thread.currentThread());
                wrote.countDown();
                return null;
            }).when(mockWriter).write(any(), any());

            pollCaptor.getValue().onPollComplete(Collections.emptyMap());

            assertThat(wrote.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(writeThread.get().getName()).isEqualTo("test-snapshot-writer");
            provider.shutdown();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void multiplePollsCoalesceIntoSingleWrite(@TempDir Path dir) throws Exception {
        RemoteFlagProvider mockRemote = mock(RemoteFlagProvider.class);
        FileFlagProvider mockFile = mock(FileFlagProvider.class);
        SnapshotWriter mockWriter = mock(SnapshotWriter.class);
        when(mockRemote.getState()).thenReturn(ProviderState.READY);
        when(mockFile.getState()).thenReturn(ProviderState.READY);

        ManualExecutor executor = new ManualExecutor();
        ArgumentCaptor<RemotePollListener> pollCaptor = ArgumentCaptor.forClass(RemotePollListener.class);
        HybridFlagProvider provider = buildProvider(mockRemote, mockFile, mockWriter, dir, executor);
        provider.init();
        verify(mockRemote, atLeastOnce()).setPollListener(pollCaptor.capture());

        // Init does a synchronous baseline write before the poll listener fires;
        // the executor queue should still be empty after init.
        assertThat(executor.queue).isEmpty();
        clearInvocations(mockWriter);

        // Three rapid polls — only the first should enqueue; subsequent ones
        // update pendingSnapshot but do not enqueue extra tasks. Maps are
        // distinct instances so eq(s3) is a real discriminator.
        io.github.eliasss3990.openflags.core.model.Flag flag = new io.github.eliasss3990.openflags.core.model.Flag(
                "k",
                io.github.eliasss3990.openflags.core.model.FlagType.BOOLEAN,
                io.github.eliasss3990.openflags.core.model.FlagValue.of(false, io.github.eliasss3990.openflags.core.model.FlagType.BOOLEAN),
                true,
                java.util.Collections.emptyMap());
        Map<String, io.github.eliasss3990.openflags.core.model.Flag> s1 = Map.of("a", flag);
        Map<String, io.github.eliasss3990.openflags.core.model.Flag> s2 = Map.of("b", flag);
        Map<String, io.github.eliasss3990.openflags.core.model.Flag> s3 = Map.of("c", flag);
        pollCaptor.getValue().onPollComplete(s1);
        pollCaptor.getValue().onPollComplete(s2);
        pollCaptor.getValue().onPollComplete(s3);

        assertThat(executor.queue).hasSize(1);

        executor.drainOnce();

        // Only one write happened despite three polls; it carries the latest snapshot.
        verify(mockWriter, times(1)).write(eq(s3), any());
        provider.shutdown();
    }

    @Test
    void userSuppliedExecutorIsNotShutDown(@TempDir Path dir) {
        RemoteFlagProvider mockRemote = mock(RemoteFlagProvider.class);
        FileFlagProvider mockFile = mock(FileFlagProvider.class);
        SnapshotWriter mockWriter = mock(SnapshotWriter.class);
        when(mockRemote.getState()).thenReturn(ProviderState.READY);
        when(mockFile.getState()).thenReturn(ProviderState.READY);

        ExecutorService userExecutor = Executors.newSingleThreadExecutor();
        try {
            HybridFlagProvider provider = buildProvider(mockRemote, mockFile, mockWriter, dir, userExecutor);
            provider.init();
            provider.shutdown();
            assertThat(userExecutor.isShutdown())
                    .as("user-supplied executor must not be shut down by provider")
                    .isFalse();
        } finally {
            userExecutor.shutdownNow();
        }
    }

    @Test
    void shutdownDrainsPendingSnapshot(@TempDir Path dir) throws Exception {
        RemoteFlagProvider mockRemote = mock(RemoteFlagProvider.class);
        FileFlagProvider mockFile = mock(FileFlagProvider.class);
        SnapshotWriter mockWriter = mock(SnapshotWriter.class);
        when(mockRemote.getState()).thenReturn(ProviderState.READY);
        when(mockFile.getState()).thenReturn(ProviderState.READY);

        ManualExecutor executor = new ManualExecutor();
        ArgumentCaptor<RemotePollListener> pollCaptor = ArgumentCaptor.forClass(RemotePollListener.class);
        HybridFlagProvider provider = buildProvider(mockRemote, mockFile, mockWriter, dir, executor);
        provider.init();
        verify(mockRemote, atLeastOnce()).setPollListener(pollCaptor.capture());
        clearInvocations(mockWriter);

        Map<String, io.github.eliasss3990.openflags.core.model.Flag> snap = Collections.emptyMap();
        pollCaptor.getValue().onPollComplete(snap);
        // Task is queued but not yet run — pretend the executor is overloaded.
        assertThat(executor.queue).hasSize(1);

        // Shutdown should drain the pending snapshot synchronously even though
        // the executor never ran the queued task.
        provider.shutdown();
        verify(mockWriter, times(1)).write(eq(snap), any());
    }
}
