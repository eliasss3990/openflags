package com.openflags.core.event;

import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;

import java.util.Optional;

/**
 * Emitted by a {@link com.openflags.core.provider.FlagProvider} when a flag's value or state changes.
 * <p>
 * Implemented as a Java record (ADR-009).
 * </p>
 *
 * @param flagKey    the key of the flag that changed
 * @param flagType   the data type of the flag
 * @param oldValue   the previous value; empty for {@link ChangeType#CREATED} events
 * @param newValue   the new value; empty for {@link ChangeType#DELETED} events
 * @param changeType the kind of change
 */
public record FlagChangeEvent(
        String flagKey,
        FlagType flagType,
        Optional<FlagValue> oldValue,
        Optional<FlagValue> newValue,
        ChangeType changeType
) {}
