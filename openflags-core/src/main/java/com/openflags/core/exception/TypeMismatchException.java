package com.openflags.core.exception;

import com.openflags.core.model.FlagType;

/**
 * Thrown when the type requested by the caller does not match the flag's actual type.
 */
public class TypeMismatchException extends OpenFlagsException {

    private final String flagKey;
    private final FlagType expectedType;
    private final FlagType actualType;

    /**
     * Creates a type mismatch exception.
     *
     * @param flagKey      the key of the flag (may be null when used in value construction)
     * @param expectedType the type the caller requested
     * @param actualType   the type the flag actually has (may be null for unrecognized values)
     */
    public TypeMismatchException(String flagKey, FlagType expectedType, FlagType actualType) {
        super(buildMessage(flagKey, expectedType, actualType));
        this.flagKey = flagKey;
        this.expectedType = expectedType;
        this.actualType = actualType;
    }

    /**
     * Returns the key of the flag involved in the mismatch.
     *
     * @return the flag key, or null if not applicable
     */
    public String getFlagKey() {
        return flagKey;
    }

    /**
     * Returns the type that was requested.
     *
     * @return the expected flag type
     */
    public FlagType getExpectedType() {
        return expectedType;
    }

    /**
     * Returns the actual type of the flag value.
     *
     * @return the actual flag type, or null if the value type is unrecognized
     */
    public FlagType getActualType() {
        return actualType;
    }

    private static String buildMessage(String flagKey, FlagType expected, FlagType actual) {
        if (flagKey != null) {
            return "Type mismatch for flag '" + flagKey + "': expected " + expected + ", got " + actual;
        }
        return "Type mismatch: expected " + expected + ", got " + actual;
    }
}
