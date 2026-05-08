package com.openflags.provider.remote;

import java.io.IOException;

/**
 * Thrown when the HTTP response body from the remote flags endpoint exceeds
 * the configured {@code maxResponseBytes} limit.
 *
 * <p>
 * This is a subclass of {@link IOException} so it propagates naturally
 * through the {@code fetch()} call chain alongside other I/O errors.
 */
public final class ResponseTooLargeException extends IOException {

    /** Configured byte cap that was exceeded. */
    private final long limit;
    /** Number of bytes observed when the limit was exceeded. */
    private final long seen;

    /**
     * Creates an exception describing a response that exceeded the configured cap.
     *
     * @param limit the configured byte cap
     * @param seen  the number of bytes read before the limit was exceeded
     *              ({@code limit + 1} when not otherwise known)
     */
    public ResponseTooLargeException(long limit, long seen) {
        super("Response body exceeded limit of " + limit + " bytes (read at least " + seen + " bytes)");
        this.limit = limit;
        this.seen = seen;
    }

    /**
     * Returns the configured byte cap.
     *
     * @return byte cap configured via {@code maxResponseBytes}
     */
    public long limit() {
        return limit;
    }

    /**
     * Returns the number of bytes observed when the limit was exceeded.
     *
     * @return bytes read before the limit was hit ({@code limit + 1} when not otherwise known)
     */
    public long seen() {
        return seen;
    }
}
