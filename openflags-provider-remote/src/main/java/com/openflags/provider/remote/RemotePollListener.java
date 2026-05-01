package com.openflags.provider.remote;

import com.openflags.core.model.Flag;

import java.util.Map;

/**
 * Package-private callback invoked once per successful poll cycle by
 * {@link RemoteFlagProvider}.
 * <p>
 * Unlike {@link com.openflags.core.event.FlagChangeListener} (which fires
 * per-flag), this
 * interface fires once per poll cycle, after all individual change events have
 * been emitted.
 * Consumers receive a stable, immutable snapshot of the full flag map for that
 * cycle.
 * </p>
 */
public interface RemotePollListener {

    /**
     * Called once after a successful poll has been processed and all
     * {@link com.openflags.core.event.FlagChangeEvent}s have been emitted.
     *
     * @param snapshot an immutable view of the complete flag set as of this poll
     */
    void onPollComplete(Map<String, Flag> snapshot);

    /**
     * Called once per poll attempt regardless of outcome (success, failure,
     * not_modified, ...).
     * Default no-op for backward compatibility.
     *
     * @param outcome       short tag matching {@code MetricsRecorder#recordPoll}
     *                      conventions; one of {@code "success"}, {@code "failure"}
     *                      or {@code "not_modified"}
     * @param durationNanos wall-clock duration of the poll in nanoseconds;
     *                      non-negative
     */
    default void onPollOutcome(String outcome, long durationNanos) {
        // no-op
    }
}
