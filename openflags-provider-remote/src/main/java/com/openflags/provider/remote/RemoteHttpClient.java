package com.openflags.provider.remote;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Thin wrapper around {@link java.net.http.HttpClient} that owns the client instance,
 * builds requests with the configured auth header and user agent, and exposes a single
 * {@code fetch()} entry point used by both {@code init()} and the polling task.
 * <p>
 * Package-private: this is an implementation detail of {@link RemoteFlagProvider}.
 * </p>
 */
final class RemoteHttpClient {

    private final HttpClient httpClient;
    private final RemoteProviderConfig config;
    private final URI targetUri;

    RemoteHttpClient(RemoteProviderConfig config) {
        this.config = config;
        // URI.resolve() drops the base path when flagsPath starts with '/'.
        // Concatenate explicitly to preserve base path segments (e.g. /api/v1).
        this.targetUri = buildTargetUri(config.baseUrl().toString(), config.flagsPath());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    /**
     * Performs a synchronous GET against the configured URL.
     *
     * @return the HTTP response with body as String (UTF-8)
     * @throws IOException          on I/O failure
     * @throws InterruptedException if interrupted while waiting
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
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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
            } catch (Exception ignored) {
                // best effort
            }
        }
    }
}
