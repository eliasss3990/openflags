package com.openflags.core.model;

import com.openflags.core.exception.TypeMismatchException;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Type-safe wrapper around a feature flag's value.
 * <p>
 * Provides typed accessor methods with validation. Instances are immutable.
 * Use {@link #of(Object, FlagType)} to create instances.
 * </p>
 * <p>
 * This is a final class rather than a record because it requires typed accessor
 * methods with validation logic that do not fit the record pattern (ADR-009).
 * </p>
 */
public final class FlagValue {

    private final Object rawValue;
    private final FlagType type;

    private FlagValue(Object rawValue, FlagType type) {
        this.rawValue = rawValue;
        this.type = type;
    }

    /**
     * Creates a {@code FlagValue} from a raw object and expected type.
     * <p>
     * Validates that {@code rawValue} is compatible with the declared {@code type}:
     * BOOLEAN expects {@link Boolean}, STRING expects {@link String},
     * NUMBER expects {@link Number}, OBJECT expects {@link Map}.
     * </p>
     *
     * @param rawValue the raw value; must match the expected type
     * @param type     the flag type
     * @return a new {@code FlagValue}
     * @throws TypeMismatchException if {@code rawValue} is not compatible with {@code type}
     * @throws NullPointerException  if {@code type} is null
     */
    public static FlagValue of(Object rawValue, FlagType type) {
        Objects.requireNonNull(type, "type must not be null");
        validate(rawValue, type);
        @SuppressWarnings("unchecked")
        Object stored = (type == FlagType.OBJECT)
                ? Map.copyOf((Map<String, Object>) rawValue)
                : rawValue;
        return new FlagValue(stored, type);
    }

    /**
     * Returns the value as a {@code boolean}.
     *
     * @return the boolean value
     * @throws TypeMismatchException if this value is not of type {@link FlagType#BOOLEAN}
     */
    public boolean asBoolean() {
        requireType(FlagType.BOOLEAN);
        return (Boolean) rawValue;
    }

    /**
     * Returns the value as a {@code String}.
     *
     * @return the string value
     * @throws TypeMismatchException if this value is not of type {@link FlagType#STRING}
     */
    public String asString() {
        requireType(FlagType.STRING);
        return (String) rawValue;
    }

    /**
     * Returns the value as a {@code double}.
     *
     * @return the numeric value
     * @throws TypeMismatchException if this value is not of type {@link FlagType#NUMBER}
     */
    public double asNumber() {
        requireType(FlagType.NUMBER);
        return ((Number) rawValue).doubleValue();
    }

    /**
     * Returns the value as an unmodifiable {@link Map}.
     * <p>
     * The returned map cannot be modified; any attempt to do so throws
     * {@link UnsupportedOperationException}.
     * </p>
     *
     * @return an unmodifiable map representing the object value
     * @throws TypeMismatchException if this value is not of type {@link FlagType#OBJECT}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> asObject() {
        requireType(FlagType.OBJECT);
        return Collections.unmodifiableMap((Map<String, Object>) rawValue);
    }

    /**
     * Returns the raw underlying value.
     *
     * @return the raw value
     * @deprecated since 0.1.0, will be removed in a future release.
     *             Use the typed accessor methods ({@link #asBoolean()}, {@link #asString()},
     *             {@link #asNumber()}, {@link #asObject()}) instead.
     */
    @Deprecated(forRemoval = true)
    public Object getRawValue() {
        return rawValue;
    }

    /**
     * Returns the flag type of this value.
     *
     * @return the flag type
     */
    public FlagType getType() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FlagValue other)) return false;
        return type == other.type && Objects.equals(rawValue, other.rawValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, rawValue);
    }

    @Override
    public String toString() {
        return "FlagValue[type=" + type + ", value=" + rawValue + "]";
    }

    private void requireType(FlagType expected) {
        if (type != expected) {
            throw new TypeMismatchException(null, expected, type);
        }
    }

    private static void validate(Object rawValue, FlagType type) {
        if (!isCompatible(rawValue, type)) {
            throw new TypeMismatchException(null, type, rawValue == null ? null : inferType(rawValue));
        }
    }

    private static boolean isCompatible(Object value, FlagType type) {
        return switch (type) {
            case BOOLEAN -> value instanceof Boolean;
            case STRING  -> value instanceof String;
            case NUMBER  -> value instanceof Number;
            case OBJECT  -> value instanceof Map<?, ?>;
        };
    }

    private static FlagType inferType(Object value) {
        if (value instanceof Boolean) return FlagType.BOOLEAN;
        if (value instanceof String) return FlagType.STRING;
        if (value instanceof Number) return FlagType.NUMBER;
        if (value instanceof Map<?, ?>) return FlagType.OBJECT;
        return null;
    }
}
