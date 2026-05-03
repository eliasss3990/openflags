package com.openflags.provider.remote;

/**
 * Desired HTTP protocol version for the remote flag provider's HTTP client.
 *
 * <ul>
 *   <li>{@link #AUTO} — let the JDK negotiate; uses the scheme heuristic:
 *       {@code https} URLs attempt HTTP/2, {@code http} URLs default to
 *       HTTP/1.1.</li>
 *   <li>{@link #HTTP_1_1} — force HTTP/1.1 regardless of scheme.</li>
 *   <li>{@link #HTTP_2} — force HTTP/2; requires the server to support it
 *       (h2 over TLS or h2c over plain text).</li>
 * </ul>
 */
public enum HttpVersion {
    /** Negotiate automatically based on the URL scheme. */
    AUTO,
    /** Force HTTP/1.1. */
    HTTP_1_1,
    /** Force HTTP/2. */
    HTTP_2
}
