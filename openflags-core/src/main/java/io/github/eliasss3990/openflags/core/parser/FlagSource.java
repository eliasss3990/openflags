package io.github.eliasss3990.openflags.core.parser;

import java.util.Objects;

/**
 * Strongly-typed identifier for the origin of a flag document, used to build
 * the {@code sourceLabel} passed to {@link FlagFileParser#parseFlags}.
 * <p>
 * Centralising the prefixes here ({@code "file:"}, {@code "remote:"}) prevents
 * drift between providers and keeps error messages consistent across modules.
 * </p>
 */
public enum FlagSource {

    /** Flag document loaded from a local filesystem path. */
    FILE("file"),

    /** Flag document fetched from an HTTP backend. */
    REMOTE("remote");

    private final String prefix;

    FlagSource(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Returns the lowercase prefix used in error labels, without the trailing
     * colon.
     *
     * @return the source prefix
     */
    public String prefix() {
        return prefix;
    }

    /**
     * Builds a parser source label of the form {@code "<prefix>:<detail>"}.
     *
     * @param detail provider-specific detail (file name, base URL, etc.); must not
     *               be null
     * @return the formatted label
     * @throws NullPointerException if {@code detail} is null
     */
    public String label(String detail) {
        Objects.requireNonNull(detail, "detail must not be null");
        return prefix + ":" + detail;
    }
}
