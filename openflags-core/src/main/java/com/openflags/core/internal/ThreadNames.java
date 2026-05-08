package com.openflags.core.internal;

/**
 * Centralized thread-name constants used across openflags modules.
 * <p>
 * Keeping these names in one place lets operators recognise openflags-owned
 * threads in tooling (jstack, profilers, log MDC) and avoids drift between
 * modules.
 * </p>
 * <p>
 * <strong>Internal API.</strong> Not part of the public contract.
 * </p>
 */
public final class ThreadNames {

    /** Single-thread debounce scheduler for {@code FileWatcher}. */
    public static final String FILE_DEBOUNCE = "openflags-debounce";

    /** Filesystem watch loop thread for {@code FileWatcher}. */
    public static final String FILE_WATCHER = "openflags-filewatcher";

    /** Periodic poller thread for {@code RemoteFlagProvider}. */
    public static final String REMOTE_POLLER = "openflags-remote-poller";

    /** Off-poller snapshot writer thread for {@code HybridFlagProvider}. */
    public static final String SNAPSHOT_WRITER = "openflags-snapshot-writer";

    private ThreadNames() {
        // utility
    }
}
