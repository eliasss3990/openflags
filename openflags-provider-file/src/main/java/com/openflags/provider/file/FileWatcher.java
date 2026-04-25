package com.openflags.provider.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches a single file for modifications using {@link WatchService}.
 * <p>
 * Runs on a daemon thread and invokes the provided callback when the file changes.
 * Implements a 200ms debounce window: multiple change notifications within the window
 * are collapsed into a single callback invocation at the end of the window.
 * </p>
 *
 * <h2>Mid-write retry</h2>
 * <p>
 * If the callback throws (e.g., because the file was captured mid-write and is not yet
 * parseable), one retry is attempted after another 200ms. If the retry also fails,
 * the error is logged as a warning and the watcher continues observing.
 * </p>
 *
 * <p>Calling {@link #stop()} on an already stopped watcher is a no-op.</p>
 *
 * <p><strong>Limitation:</strong> WatchService requires a real filesystem path. Files inside
 * JARs cannot be watched. The caller is responsible for ensuring this contract.</p>
 */
public final class FileWatcher {

    private static final Logger log = LoggerFactory.getLogger(FileWatcher.class);
    private static final long DEBOUNCE_MS = 200L;

    private final Path filePath;
    private final Runnable callback;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private volatile Thread watchThread;
    private volatile ScheduledExecutorService debounceScheduler;
    private volatile ScheduledFuture<?> pendingCallback;

    /**
     * Creates a watcher for the specified file.
     *
     * @param path     the file to watch; must be a real filesystem path
     * @param callback invoked when the file changes (after the debounce window)
     */
    public FileWatcher(Path path, Runnable callback) {
        this.filePath = path;
        this.callback = callback;
    }

    /**
     * Starts watching the file. Non-blocking: spawns a daemon thread internally.
     * <p>Idempotent: calling {@code start()} on an already-started watcher is a no-op.</p>
     *
     * @throws IllegalStateException if this watcher has already been stopped
     */
    public synchronized void start() {
        if (stopped.get()) {
            throw new IllegalStateException("FileWatcher cannot be restarted after stop()");
        }
        if (watchThread != null) {
            return;
        }
        debounceScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "openflags-debounce");
            t.setDaemon(true);
            return t;
        });
        Thread t = new Thread(this::watchLoop, "openflags-filewatcher");
        t.setDaemon(true);
        t.start();
        watchThread = t;
    }

    /**
     * Stops watching and releases all resources. Idempotent.
     */
    public synchronized void stop() {
        if (stopped.compareAndSet(false, true)) {
            if (watchThread != null) {
                watchThread.interrupt();
            }
            if (debounceScheduler != null) {
                debounceScheduler.shutdownNow();
            }
        }
    }

    private void watchLoop() {
        Path dir = filePath.getParent();
        String fileName = filePath.getFileName().toString();

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            while (!stopped.get() && !Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.poll(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (key == null) continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path changed = pathEvent.context();

                    if (changed.getFileName().toString().equals(fileName)) {
                        scheduleDebounced();
                    }
                }

                if (!key.reset()) {
                    log.warn("Watch key no longer valid for directory: {}", dir);
                    break;
                }
            }
        } catch (IOException e) {
            if (!stopped.get()) {
                log.error("FileWatcher error for '{}': {}", filePath, e.getMessage());
            }
        }
    }

    private synchronized void scheduleDebounced() {
        if (pendingCallback != null && !pendingCallback.isDone()) {
            pendingCallback.cancel(false);
        }
        pendingCallback = debounceScheduler.schedule(this::invokeWithRetry, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    private void invokeWithRetry() {
        try {
            callback.run();
        } catch (Exception firstAttemptEx) {
            log.debug("Callback failed on first attempt (possibly mid-write), retrying in {}ms: {}",
                    DEBOUNCE_MS, firstAttemptEx.getMessage());
            synchronized (this) {
                pendingCallback = debounceScheduler.schedule(this::invokeRetryAttempt, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void invokeRetryAttempt() {
        try {
            callback.run();
        } catch (Exception retryEx) {
            log.warn("Callback failed on retry for file '{}': {}", filePath, retryEx.getMessage());
        }
    }
}
