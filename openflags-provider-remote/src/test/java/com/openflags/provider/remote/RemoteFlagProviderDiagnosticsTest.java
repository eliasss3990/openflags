package com.openflags.provider.remote;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteFlagProviderDiagnosticsTest {

    @Test
    void diagnosticsRedactsUserInfoInBaseUrl() {
        RemoteProviderConfig cfg = new RemoteProviderConfig(
                URI.create("https://alice:s3cret@flags.example.com/api"),
                "/flags",
                null,
                null,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                "test-agent",
                5,
                Duration.ofMinutes(5));

        RemoteFlagProvider p = new RemoteFlagProvider(cfg);
        Map<String, Object> diagnostics = p.diagnostics();
        Object baseUrl = diagnostics.get("remote.base_url");
        assertThat(baseUrl).isNotNull();
        String repr = baseUrl.toString();
        assertThat(repr).doesNotContain("alice");
        assertThat(repr).doesNotContain("s3cret");
        assertThat(repr).contains("***@flags.example.com");
    }
}
