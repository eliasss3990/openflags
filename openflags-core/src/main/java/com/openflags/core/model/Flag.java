package com.openflags.core.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable representation of a feature flag definition.
 * <p>
 * A flag has a unique key, a type, a current value, an enabled/disabled state,
 * and optional metadata. Implemented as a Java record (ADR-009).
 * </p>
 *
 * @param key      unique identifier for this flag; must match {@code ^[a-zA-Z][a-zA-Z0-9._-]*$}
 * @param type     the data type of this flag's value
 * @param value    the current value of the flag
 * @param enabled  whether this flag is active; disabled flags yield the caller's default value
 * @param metadata optional metadata (description, tags, owner, etc.); never null
 */
public record Flag(
        String key,
        FlagType type,
        FlagValue value,
        boolean enabled,
        Map<String, String> metadata
) {
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9._-]*$");

    /**
     * Compact constructor that validates all fields.
     *
     * @throws NullPointerException     if key, type, or value is null
     * @throws IllegalArgumentException if key is blank or does not match the required pattern
     * @throws IllegalArgumentException if value type does not match the declared type
     */
    public Flag {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(value, "value must not be null");

        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "key '" + key + "' does not match required pattern ^[a-zA-Z][a-zA-Z0-9._-]*$");
        }
        if (value.getType() != type) {
            throw new IllegalArgumentException(
                    "value type " + value.getType() + " does not match declared type " + type
                            + " for flag '" + key + "'");
        }
        metadata = metadata == null ? Collections.emptyMap() : Collections.unmodifiableMap(metadata);
    }
}
