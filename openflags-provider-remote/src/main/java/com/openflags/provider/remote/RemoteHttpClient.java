package com.openflags.provider.remote;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin wrapper around {@link java.net.http.HttpClient} that owns the client instance,
 * builds requests with the configured auth header and user agent, and exposes a single
 * {@code fetch()} entry point used by both {@code init()} and the polling task.
 * <p>
 * The response body is read through a capped {@link InputStream}: if the server
 * returns more than {@link RemoteProviderConfig#maxResponseBytes()} bytes the read is
 * aborted and a {@link ResponseTooLargeException} is thrown before the excess data
 * reaches application memory.
 * </p>
 * <p>
 * Package-private: this is an implementation detail of {@link RemoteFlagProvider}.
 * </p>
 */
final class RemoteHttpClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteHttpClient.class);

    private final HttpClient httpClient;
    private final RemoteProviderConfig config;
    private final URI targetUri;

    RemoteHttpClient(RemoteProviderConfig config) {
        this.config = config;
        // URI.resolve() drops the base path when flagsPath starts with '/'.
        // Concatenate explicitly to preserve base path segments (e.g. /api/v1).
        this.targetUri = buildTargetUri(config.baseUrl().toString(), config.flagsPath());
        this.httpClient = buildHttpClient(config);
    }

    private static HttpClient buildHttpClient(RemoteProviderConfig config) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER);

        switch (config.httpVersion()) {
            case HTTP_1_1 -> builder.version(HttpClient.Version.HTTP_1_1);
            case HTTP_2   -> builder.version(HttpClient.Version.HTTP_2);
            case AUTO     -> {
                // Heuristic: https → allow H2 negotiation (JDK default);
                // http → force HTTP/1.1 to avoid h2c upgrade surprises.
                if ("http".equalsIgnoreCase(config.baseUrl().getScheme())) {
                    builder.version(HttpClient.Version.HTTP_1_1);
                }
                // For https, leave the version unset so the JDK negotiates via ALPN.
            }
        }

        return builder.build();
    }

    /**
     * Performs a synchronous GET against the configured URL.
     *
     * @return the HTTP response with body as String (UTF-8)
     * @throws IOException              on I/O failure or if the body exceeds
     *                                  {@link RemoteProviderConfig#maxResponseBytes()}
     * @throws InterruptedException     if interrupted while waiting
     * @throws ResponseTooLargeException if the response body exceeds the limit
     */
    HttpResponse<String> fetch() throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .header("User-Agent", config.userAgent())
                .GET();

        if (config.authHeaderName() != null) {
            builder.header(config.authHeaderName(), config.authHeaderValue());
        }

        HttpRequest request = builder.build();
        long limit = config.maxResponseBytes();

        HttpResponse<InputStream> rawResponse =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        String body;
        try (InputStream in = rawResponse.body()) {
            body = readCapped(in, limit);
        }

        // Wrap into a String-body response so the rest of the provider is unaffected.
        return new StringBodyResponse<>(rawResponse, body);
    }

    /**
     * Reads at most {@code limit} bytes from {@code in} and returns the result as
     * a UTF-8 string. If more than {@code limit} bytes are available,
     * {@link ResponseTooLargeException} is thrown. The caller is responsible for
     * closing {@code in} (via try-with-resources) to release the underlying
     * connection back to the pool, regardless of whether this method returns
     * normally or throws.
     */
    private static String readCapped(InputStream in, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long totalRead = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        int n;
        while ((n = in.read(buffer)) != -1) {
            totalRead += n;
            if (totalRead > limit) {
                throw new ResponseTooLargeException(limit, totalRead);
            }
            out.write(buffer, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static URI buildTargetUri(String baseUrl, String flagsPath) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = flagsPath.startsWith("/") ? flagsPath : "/" + flagsPath;
        return URI.create(base + path);
    }

    void close() {
        // HttpClient in JDK 21 implements AutoCloseable; shut down underlying executor if applicable
        if (httpClient instanceof AutoCloseable ac) {
            try {
                ac.close();
            } catch (Exception e) {
                log.debug("Best-effort close of HttpClient for {} failed: {}: {}",
                        config.baseUrl(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * Minimal {@link HttpResponse} adapter that replaces the body of an existing
     * response with a pre-read {@link String} while delegating all other methods
     * to the original response.
     */
    private static final class StringBodyResponse<T> implements HttpResponse<String> {

        private final HttpResponse<T> delegate;
        private final String body;

        StringBodyResponse(HttpResponse<T> delegate, String body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override public int statusCode()                                          { return delegate.statusCode(); }
        @Override public HttpRequest request()                                     { return delegate.request(); }
        @Override public java.util.Optional<HttpResponse<String>> previousResponse() {
            // Previous responses are not needed for the remote provider use-case.
            return java.util.Optional.empty();
        }
        @Override public java.net.http.HttpHeaders headers()                      { return delegate.headers(); }
        @Override public String body()                                             { return body; }
        @Override public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return delegate.sslSession(); }
        @Override public URI uri()                                                 { return delegate.uri(); }
        @Override public HttpClient.Version version()                             { return delegate.version(); }
    }
}
