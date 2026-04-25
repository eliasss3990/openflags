package com.openflags.core.event;

/**
 * Listener for feature flag change events.
 * <p>
 * Implementations are invoked synchronously when a provider detects a flag change.
 * Implementations must be thread-safe and should not block.
 * </p>
 */
@FunctionalInterface
public interface FlagChangeListener {

    /**
     * Called when a flag changes.
     *
     * @param event the change event; never null
     */
    void onFlagChange(FlagChangeEvent event);
}
