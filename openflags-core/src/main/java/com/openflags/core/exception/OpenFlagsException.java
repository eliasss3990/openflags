package com.openflags.core.exception;

/**
 * Base exception for all openflags errors.
 * <p>
 * Unchecked (extends {@link RuntimeException}) so consumers are not forced
 * to declare or catch it. Subclasses add domain-specific context.
 * </p>
 */
public class OpenFlagsException extends RuntimeException {

    /**
     * Creates an exception with a descriptive message.
     *
     * @param message the detail message
     */
    public OpenFlagsException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and an underlying cause.
     *
     * @param message the detail message
     * @param cause   the root cause
     */
    public OpenFlagsException(String message, Throwable cause) {
        super(message, cause);
    }
}
