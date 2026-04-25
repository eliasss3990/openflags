package com.openflags.core.exception;

/**
 * Thrown when a {@link com.openflags.core.provider.FlagProvider} encounters an error,
 * such as an I/O failure, parse error, or unavailable remote endpoint.
 */
public class ProviderException extends OpenFlagsException {

    /**
     * Creates a provider exception with a descriptive message.
     *
     * @param message the detail message
     */
    public ProviderException(String message) {
        super(message);
    }

    /**
     * Creates a provider exception with a message and an underlying cause.
     *
     * @param message the detail message
     * @param cause   the root cause
     */
    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
