package com.openflags.core.event;

/**
 * Describes the kind of change that occurred on a feature flag.
 */
public enum ChangeType {
    /** The flag did not previously exist and was added. */
    CREATED,
    /** The flag existed and its value or state was modified. */
    UPDATED,
    /** The flag was removed from the provider. */
    DELETED
}
