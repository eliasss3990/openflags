package com.openflags.provider.hybrid;

import com.openflags.core.event.ChangeType;
import com.openflags.core.event.FlagChangeEvent;
import com.openflags.core.event.FlagChangeListener;
import com.openflags.core.metrics.MetricsRecorder;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import com.openflags.core.provider.ProviderState;
import com.openflags.provider.file.FileFlagProvider;
import com.openflags.provider.remote.RemoteFlagProvider;
import com.openflags.provider.remote.RemotePollListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
                return new HybridFlagProvider(cfg, remote, file, writer);
        }

        private static FlagChangeEvent sampleFileEvent() {
                return new FlagChangeEvent("flag-x", FlagType.BOOLEAN,
                                Optional.of(FlagValue.of(false, FlagType.BOOLEAN)),
                                Optional.of(FlagValue.of(true, FlagType.BOOLEAN)),
                                ChangeType.UPDATED);
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

                assertThat(provider.diagnostics()).containsEntry("hybrid.last_snapshot_write", "");

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

        private static AtomicBoolean readExpectingSelfWriteFlag(HybridFlagProvider provider)
                        throws ReflectiveOperationException {
                java.lang.reflect.Field field = HybridFlagProvider.class
                                .getDeclaredField("expectingSelfWrite");
                field.setAccessible(true);
                return (AtomicBoolean) field.get(provider);
        }
}
