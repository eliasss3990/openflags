package com.openflags.core;

/**
 * Public catalog of MDC (Mapped Diagnostic Context) keys that openflags sets
 * around each evaluation when MDC propagation is enabled on the client.
 *
 * <p>
 * <strong>API stability:</strong> these keys are part of the public contract
 * since 1.0. Logback/Log4j patterns and JSON appenders that reference them
 * (e.g. {@code %X{openflags.flag_key}}) would break on rename, so any change
 * is a <em>major</em> version bump.
 *
 * @apiNote Reference these constants from log layout configurations when
 *          building patterns programmatically; they are also useful in
 *          tests asserting on captured MDC content.
 */
public final class OpenFlagsMdc {

    private OpenFlagsMdc() {
    }

    /** MDC key carrying the flag key being evaluated. */
    public static final String FLAG_KEY = "openflags.flag_key";

    /** MDC key carrying the evaluation context's targeting key, when present. */
    public static final String TARGETING_KEY = "openflags.targeting_key";
}
