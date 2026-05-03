package com.openflags.core.event;

import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;

/**
 * Describes the kind of change that occurred on a feature flag.
 *
 * <p>Precedence when resolving the change type for a single transition:
 * {@code CREATED > DELETED > ENABLED/DISABLED > UPDATED}.
 * That is, if a flag is newly created with {@code value=true} the event type is
 * {@code CREATED}, not {@code ENABLED}.
 *
 * <p>{@code ENABLED} and {@code DISABLED} only apply to {@link FlagType#BOOLEAN}
 * flags whose value transitions between {@code false} and {@code true} (or vice-versa)
 * as part of an update (not a creation or deletion).
 */
public enum ChangeType {
    /** The flag did not previously exist and was added. */
    CREATED,
    /** The flag existed and its value or state was modified. */
    UPDATED,
    /** The flag was removed from the provider. */
    DELETED,
    /**
     * A boolean flag transitioned from {@code false} to {@code true}.
     * Only emitted when the flag already existed (not on creation).
     */
    ENABLED,
    /**
     * A boolean flag transitioned from {@code true} to {@code false}.
     * Only emitted when the flag still exists (not on deletion).
     */
    DISABLED;

    /**
     * Resolves the change type for an update transition (the flag already existed and
     * still exists after the change).
     *
     * <p>Returns {@link #ENABLED} when {@code flagType} is {@link FlagType#BOOLEAN} and the
     * value transitions from {@code false} to {@code true}.
     * Returns {@link #DISABLED} when {@code flagType} is {@link FlagType#BOOLEAN} and the
     * value transitions from {@code true} to {@code false}.
     * Falls back to {@link #UPDATED} in all other cases (non-boolean flags, equal boolean
     * values, or value types that do not match the declared flag type).
     *
     * @param flagType the type of the flag
     * @param oldValue the previous value; must not be {@code null}
     * @param newValue the new value; must not be {@code null}
     * @return the resolved change type
     */
    public static ChangeType resolveUpdate(FlagType flagType, FlagValue oldValue, FlagValue newValue) {
        if (flagType != FlagType.BOOLEAN) {
            return UPDATED;
        }
        if (oldValue.getType() != FlagType.BOOLEAN || newValue.getType() != FlagType.BOOLEAN) {
            return UPDATED;
        }
        boolean oldBool = oldValue.asBoolean();
        boolean newBool = newValue.asBoolean();
        if (!oldBool && newBool) {
            return ENABLED;
        }
        if (oldBool && !newBool) {
            return DISABLED;
        }
        return UPDATED;
    }
}
