package com.openflags.core.exception;

/**
 * Thrown when a requested flag key does not exist in the provider.
 */
public class FlagNotFoundException extends OpenFlagsException {

    private final String flagKey;

    /**
     * Creates the exception for the given flag key.
     *
     * @param flagKey the key that was not found
     */
    public FlagNotFoundException(String flagKey) {
        super("Flag not found: '" + flagKey + "'");
        this.flagKey = flagKey;
    }

    /**
     * Returns the key of the flag that was not found.
     *
     * @return the flag key
     */
    public String getFlagKey() {
        return flagKey;
    }
}
