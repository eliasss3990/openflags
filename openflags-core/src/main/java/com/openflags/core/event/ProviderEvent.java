package com.openflags.core.event;

import com.openflags.core.provider.ProviderState;

import java.util.Optional;

/**
 * Emitted when a provider's lifecycle state changes.
 * <p>
 * Implemented as a Java record (ADR-009).
 * </p>
 *
 * @param state   the new state of the provider
 * @param message an optional descriptive message (e.g., error details or reason for the change)
 */
public record ProviderEvent(
        ProviderState state,
        Optional<String> message
) {}
