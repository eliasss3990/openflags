package com.openflags.core.util;

import java.net.URI;
import java.util.Objects;

/**
 * Internal helpers for URI handling.
 */
public final class Urls {

    private static final String REDACTED = "***";

    private Urls() {
    }

    /**
     * Returns a string representation of the given URI with any user-info component
     * (credentials embedded as {@code user:password@host}) replaced by
     * {@value #REDACTED}.
     * Intended for logs and diagnostic outputs where exposing credentials would
     * leak secrets.
     *
     * @param uri the URI to render; must not be null
     * @return a redacted string form of the URI
     */
    public static String redact(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");
        String userInfo = uri.getUserInfo();
        if (userInfo == null) {
            return uri.toString();
        }
        try {
            URI sanitized = new URI(
                    uri.getScheme(),
                    REDACTED,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment());
            return sanitized.toString();
        } catch (java.net.URISyntaxException e) {
            StringBuilder sb = new StringBuilder();
            if (uri.getScheme() != null)
                sb.append(uri.getScheme()).append("://");
            sb.append(REDACTED).append('@');
            if (uri.getHost() != null)
                sb.append(uri.getHost());
            if (uri.getPort() != -1)
                sb.append(':').append(uri.getPort());
            if (uri.getPath() != null)
                sb.append(uri.getPath());
            return sb.toString();
        }
    }
}
