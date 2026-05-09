package io.github.eliasss3990.openflags.provider.hybrid;

import io.github.eliasss3990.openflags.core.event.ChangeType;
import io.github.eliasss3990.openflags.core.event.FlagChangeEvent;
import io.github.eliasss3990.openflags.core.event.FlagChangeListener;
import io.github.eliasss3990.openflags.core.metrics.MetricsRecorder;
import io.github.eliasss3990.openflags.core.model.FlagType;
import io.github.eliasss3990.openflags.core.model.FlagValue;
import io.github.eliasss3990.openflags.core.provider.ProviderState;
import io.github.eliasss3990.openflags.provider.file.FileFlagProvider;
import io.github.eliasss3990.openflags.provider.remote.RemoteFlagProvider;
import io.github.eliasss3990.openflags.provider.remote.RemotePollListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for F7 (snapshot write timestamp + G5 metric). Verifies
 * that {@code lastSnapshotWriteAt} is updated only after a successful write,
 * that a failed write does not silence subsequent external file events, and
 * that {@link MetricsRecorder#recordSnapshotWrite(String, long)} is invoked
 * on both success and failure paths.
 */
class HybridSnapshotWriteTest {

        private HybridFlagProvider buildProvider(RemoteFlagProvider remote,
                        FileFlagProvider file,
                        SnapshotWriter writer,
                        Path dir) {
                HybridProviderConfig cfg = new HybridProviderConfig(
                                HybridFlagProviderTest.REMOTE_CFG,
                                dir.resolve("snap.json"),
                                SnapshotFormat.JSON,
                                false,
                                Duration.ofMillis(200),
                                false);
                return new HybridFlagProvider(cfg, remote, file, writer, Runnable::run);
        }

        private static FlagChangeEvent sampleFileEvent() {
                return new FlagChangeEvent("flag-x", FlagType.BOOLEAN,
                                Optional.of(FlagValue.of(false, FlagType.BOOLEAN)),
                                Optional.of(FlagValue.of(true, FlagType.BOOLEAN)),
                                ChangeType.ENABLED);
        }

        @Test
        void writeFailure_doesNotSilenceSubsequentFileEvent(@TempDir Path dir) throws Exception {
                RemoteFlagProvider mockRemote = mock(RemoteFlagProvider.class);
                FileFlagProvider mockFile = mock(FileFlagProvider.class);
                SnapshotWriter mockWriter = mock(SnapshotWriter.class);
                // Routing must hit the file branch in onFileChange (remote=ERROR).
                when(mockRemote.getState()).thenReturn(ProviderState.ERROR);
                when(mockFile.getState()).thenReturn(ProviderState.READY);
                doThrow(new IOException("boom")).when(mockWriter).write(any(), any());

                ArgumentCaptor<FlagChangeListener> fileListenerCaptor = ArgumentCaptor
                                .forClass(FlagChangeListener.class);
                ArgumentCaptor<RemotePollListener> pollListenerCaptor = ArgumentCaptor
                                .forClass(RemotePollListener.class);

                HybridFlagProvider provider = buildProvider(mockRemote, mockFile, mockWriter, dir);
                provider.init();
                verify(mockFile).addChangeListener(fileListenerCaptor.capture());
                verify(mockRemote, atLeastOnce()).setPollListener(pollListenerCaptor.capture());

                FlagChangeListener publicListener = mock(FlagChangeListener.class);
                provider.addChangeListener(publicListener);

                // Simulate a poll that triggers a failed snapshot write.
                pollListenerCaptor.getValue().onPollComplete(Collections.emptyMap());

                // External file event arrives. With the bug, the pre-write timestamp
                // would silence it; after the fix, it is forwarded.
                FlagChangeEvent fileEvent = sampleFileEvent();
                fileListenerCaptor.getValue().onFlagChange(fileEvent);

                verify(publicListener).onFlagChange(fileEvent);
                provider.shutdown();
        }

        @Test
        void writeSuccess_setsLastSnapshotWriteTimestamp(@TempDir Path dir) {
                RemoteFlagProvider mockRemote = mock(RemoteFlagProvider.class);
                FileFlagProvider mockFile = mock(FileFlagProvider.class);
                SnapshotWriter mockWriter = mock(SnapshotWriter.class);
                when(mockRemote.getState()).thenReturn(ProviderState.READY);
                when(mockFile.getState()).thenReturn(ProviderState.READY);

                ArgumentCaptor<RemotePollListener> pollListenerCaptor = ArgumentCaptor
                                .forClass(RemotePollListener.class);

                HybridFlagProvider provider = buildProvider(mockRemote, mockFile, mockWriter, dir);
                provider.init();
                verify(mockRemote, atLeastOnce()).setPollListener(pollListenerCaptor.capture());

                // Baseline write at end of init() already populates the timestamp.
                assertThat(provider.diagnostics().get("hybrid.last_snapshot_write"))
                                .asString()
                                .isNotEmpty();

                pollListenerCaptor.getValue().onPollComplete(Collections.emptyMap());

                assertThat(provider.diagnostics().get("hybrid.last_snapshot_write"))
                                .asString()
                                .isNotEmpty();
                provider.shutdown();
        }

        @Test
        void writeFailure_doesNotUpdateTimestamp(@TempDir Path dir) throws Exception {
                RemoteFlagProvider mockRemote = mock(RemoteFlagProvider.class);
                FileFlagProvider mockFile = mock(FileFlagProvider.class);
                SnapshotWriter mockWriter = mock(SnapshotWriter.class);
                when(mockRemote.getState()).thenReturn(ProviderState.READY);
                when(mockFile.getState()).thenReturn(ProviderState.READY);
                doThrow(new IOException("disk full")).when(mockWriter).write(any(), any());

                ArgumentCaptor<RemotePollListener> pollListenerCaptor = ArgumentCaptor
                                .forClass(RemotePollListener.class);

                HybridFlagProvider provider = buildProvider(mockRemote, mockFile, mockWriter, dir);
                provider.init();
                verify(mockRemote, atLeastOnce()).setPollListener(pollListenerCaptor.capture());

                pollListenerCaptor.getValue().onPollComplete(Collections.emptyMap());

                assertThat(provider.diagnostics()).containsEntry("hybrid.last_snapshot_write", "");
                provider.shutdown();
        }

        @Test
        void writeSuccess_recordsSuccessMetric(@TempDir Path dir) {
                RemoteFlagProvider mockRemote = mock(RemoteFlagProvider.class);
                FileFlagProvider mockFile = mock(FileFlagProvider.class);
                SnapshotWriter mockWriter = mock(SnapshotWriter.class);
                MetricsRecorder recorder = mock(MetricsRecorder.class);
                when(mockRemote.getState()).thenReturn(ProviderState.READY);
                when(mockFile.getState()).thenReturn(ProviderState.READY);

                ArgumentCaptor<RemotePollListener> pollListenerCaptor = ArgumentCaptor
                                .forClass(RemotePollListener.class);

                HybridFlagProvider provider = buildProvider(mockRemote, mockFile, mockWriter, dir);
                provider.setMetricsRecorder(recorder);
                provider.init();
                verify(mockRemote, atLeastOnce()).setPollListener(pollListenerCaptor.capture());

                // Discard baseline-write metric invocation so we verify only the poll-driven write.
                clearInvocations(recorder);

                pollListenerCaptor.getValue().onPollComplete(Collections.emptyMap());

                verify(recorder).recordSnapshotWrite(eq("success"), anyLong());
                verify(recorder, never()).recordSnapshotWrite(eq("failure"), anyLong());
                provider.shutdown();
        }

        @Test
        void writeFailure_recordsFailureMetric(@TempDir Path dir) throws Exception {
                RemoteFlagProvider mockRemote = mock(RemoteFlagProvider.class);
                FileFlagProvider mockFile = mock(FileFlagProvider.class);
                SnapshotWriter mockWriter = mock(SnapshotWriter.class);
                MetricsRecorder recorder = mock(MetricsRecorder.class);
                when(mockRemote.getState()).thenReturn(ProviderState.READY);
                when(mockFile.getState()).thenReturn(ProviderState.READY);
                doThrow(new IOException("boom")).when(mockWriter).write(any(), any());

                ArgumentCaptor<RemotePollListener> pollListenerCaptor = ArgumentCaptor
                                .forClass(RemotePollListener.class);

                HybridFlagProvider provider = buildProvider(mockRemote, mockFile, mockWriter, dir);
                provider.setMetricsRecorder(recorder);
                provider.init();
                verify(mockRemote, atLeastOnce()).setPollListener(pollListenerCaptor.capture());

                // Discard baseline-write metric invocation so we verify only the poll-driven write.
                clearInvocations(recorder);

                pollListenerCaptor.getValue().onPollComplete(Collections.emptyMap());

                verify(recorder).recordSnapshotWrite(eq("failure"), anyLong());
                verify(recorder, never()).recordSnapshotWrite(eq("success"), anyLong());
                provider.shutdown();
        }

        @Test
        void writeSuccess_selfFileEventIsFiltered(@TempDir Path dir) throws Exception {
                RemoteFlagProvider mockRemote = mock(RemoteFlagProvider.class);
                FileFlagProvider mockFile = mock(FileFlagProvider.class);
                SnapshotWriter mockWriter = mock(SnapshotWriter.class);
                // Use ERROR so onFileChange would otherwise forward the event.
                when(mockRemote.getState()).thenReturn(ProviderState.ERROR);
                when(mockFile.getState()).thenReturn(ProviderState.READY);

                ArgumentCaptor<FlagChangeListener> fileListenerCaptor = ArgumentCaptor
                                .forClass(FlagChangeListener.class);
                ArgumentCaptor<RemotePollListener> pollListenerCaptor = ArgumentCaptor
                                .forClass(RemotePollListener.class);

                HybridFlagProvider provider = buildProvider(mockRemote, mockFile, mockWriter, dir);
                provider.init();
                verify(mockFile).addChangeListener(fileListenerCaptor.capture());
                verify(mockRemote, atLeastOnce()).setPollListener(pollListenerCaptor.capture());

                FlagChangeListener publicListener = mock(FlagChangeListener.class);
                provider.addChangeListener(publicListener);

                pollListenerCaptor.getValue().onPollComplete(Collections.emptyMap());

                // Read the internal flag via reflection to assert the
                // consume-once contract directly, rather than depending on the
                // debounce window also being a filter (which would mask a
                // regression where expectingSelfWrite stopped working).
                AtomicBoolean expectingSelfWrite = readExpectingSelfWriteFlag(provider);
                assertThat(expectingSelfWrite.get())
                                .as("flag must be armed after a successful self-write")
                                .isTrue();

                FlagChangeEvent selfEvent = sampleFileEvent();
                fileListenerCaptor.getValue().onFlagChange(selfEvent);

                assertThat(expectingSelfWrite.get())
                                .as("flag must be cleared after one onFileChange invocation")
                                .isFalse();
                verify(publicListener, never()).onFlagChange(selfEvent);
                provider.shutdown();
        }

        /**
         * ADR-2 invariant: HybridFlagProvider must not have any listener
         * registered with the sub-providers while their {@code init()} is
         * running. A listener registered too early would expose remote.init()'s
         * synchronous first poll to {@code onPollComplete} and cause a
         * snapshot write to race with the FileWatcher coming up in
         * file.init().
         */
        @Test
        void firstPollDuringInitDoesNotWriteSnapshot(@TempDir Path dir) throws Exception {
                RemoteFlagProvider mockRemote = mock(RemoteFlagProvider.class);
                FileFlagProvider mockFile = mock(FileFlagProvider.class);
                SnapshotWriter mockWriter = mock(SnapshotWriter.class);
                when(mockRemote.getState()).thenReturn(ProviderState.READY);
                when(mockFile.getState()).thenReturn(ProviderState.READY);

                AtomicReference<RemotePollListener> pollListenerRef = new AtomicReference<>();
                AtomicReference<FlagChangeListener> remoteListenerRef = new AtomicReference<>();
                AtomicReference<FlagChangeListener> fileListenerRef = new AtomicReference<>();
                AtomicBoolean pollListenerSetDuringRemoteInit = new AtomicBoolean();
                AtomicBoolean remoteListenerSetDuringRemoteInit = new AtomicBoolean();
                AtomicBoolean fileListenerSetDuringFileInit = new AtomicBoolean();
                AtomicBoolean writerInvokedDuringRemoteInit = new AtomicBoolean();
                AtomicBoolean writerInvokedDuringFileInit = new AtomicBoolean();
                AtomicBoolean inRemoteInit = new AtomicBoolean();
                AtomicBoolean inFileInit = new AtomicBoolean();

                doAnswer(inv -> {
                        if (inRemoteInit.get()) {
                                writerInvokedDuringRemoteInit.set(true);
                        }
                        if (inFileInit.get()) {
                                writerInvokedDuringFileInit.set(true);
                        }
                        return null;
                }).when(mockWriter).write(any(), any());

                doAnswer(inv -> {
                        pollListenerRef.set(inv.getArgument(0));
                        return null;
                }).when(mockRemote).setPollListener(any());
                doAnswer(inv -> {
                        remoteListenerRef.set(inv.getArgument(0));
                        return null;
                }).when(mockRemote).addChangeListener(any());
                doAnswer(inv -> {
                        fileListenerRef.set(inv.getArgument(0));
                        return null;
                }).when(mockFile).addChangeListener(any());

                doAnswer(inv -> {
                        inRemoteInit.set(true);
                        try {
                                pollListenerSetDuringRemoteInit.set(pollListenerRef.get() != null);
                                remoteListenerSetDuringRemoteInit.set(remoteListenerRef.get() != null);
                        } finally {
                                inRemoteInit.set(false);
                        }
                        return null;
                }).when(mockRemote).init();

                doAnswer(inv -> {
                        inFileInit.set(true);
                        try {
                                fileListenerSetDuringFileInit.set(fileListenerRef.get() != null);
                        } finally {
                                inFileInit.set(false);
                        }
                        return null;
                }).when(mockFile).init();

                HybridFlagProvider provider = buildProvider(mockRemote, mockFile, mockWriter, dir);
                provider.init();

                assertThat(pollListenerSetDuringRemoteInit.get())
                                .as("pollListener must NOT be registered while remote.init() runs")
                                .isFalse();
                assertThat(remoteListenerSetDuringRemoteInit.get())
                                .as("remote change listener must NOT be registered while remote.init() runs")
                                .isFalse();
                assertThat(fileListenerSetDuringFileInit.get())
                                .as("file change listener must NOT be registered while file.init() runs")
                                .isFalse();
                assertThat(writerInvokedDuringRemoteInit.get())
                                .as("snapshotWriter must NOT be invoked while remote.init() runs")
                                .isFalse();
                assertThat(writerInvokedDuringFileInit.get())
                                .as("snapshotWriter must NOT be invoked while file.init() runs")
                                .isFalse();
                assertThat(pollListenerRef.get())
                                .as("pollListener must be registered after init() completes")
                                .isNotNull();

                provider.shutdown();
        }

        private static AtomicBoolean readExpectingSelfWriteFlag(HybridFlagProvider provider)
                        throws ReflectiveOperationException {
                java.lang.reflect.Field field = HybridFlagProvider.class
                                .getDeclaredField("expectingSelfWrite");
                field.setAccessible(true);
                return (AtomicBoolean) field.get(provider);
        }
}
