package com.openflags.provider.remote;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RemoteProviderConfigTest {

    private static final URI HTTP_URL  = URI.create("http://flags.example.com");
    private static final URI HTTPS_URL = URI.create("https://flags.example.com");
    private static final Duration POS  = Duration.ofSeconds(5);
    private static final Duration POLL = Duration.ofSeconds(5);
    private static final Duration TTL  = Duration.ofSeconds(30);

    private RemoteProviderConfig valid() {
        return new RemoteProviderConfig(HTTPS_URL, "/flags", null, null, POS, POS, POLL, TTL, "agent");
    }

    @Test
    void validConfig_httpScheme() {
        RemoteProviderConfig c = new RemoteProviderConfig(HTTP_URL, "/flags", null, null, POS, POS, POLL, TTL, "agent");
        assertThat(c.baseUrl()).isEqualTo(HTTP_URL);
    }

    @Test
    void validConfig_httpsScheme() {
        assertThat(valid().baseUrl()).isEqualTo(HTTPS_URL);
    }

    @Test
    void defaultFlagsPath_whenNull() {
        RemoteProviderConfig c = new RemoteProviderConfig(HTTPS_URL, null, null, null, POS, POS, POLL, TTL, "agent");
        assertThat(c.flagsPath()).isEqualTo("/flags");
    }

    @Test
    void defaultFlagsPath_whenBlank() {
        RemoteProviderConfig c = new RemoteProviderConfig(HTTPS_URL, "  ", null, null, POS, POS, POLL, TTL, "agent");
        assertThat(c.flagsPath()).isEqualTo("/flags");
    }

    @Test
    void defaultUserAgent_whenNull() {
        RemoteProviderConfig c = new RemoteProviderConfig(HTTPS_URL, "/flags", null, null, POS, POS, POLL, TTL, null);
        assertThat(c.userAgent()).isEqualTo("openflags-java");
    }

    @Test
    void defaultUserAgent_whenBlank() {
        RemoteProviderConfig c = new RemoteProviderConfig(HTTPS_URL, "/flags", null, null, POS, POS, POLL, TTL, "  ");
        assertThat(c.userAgent()).isEqualTo("openflags-java");
    }

    @Test
    void authHeader_bothSetAllowed() {
        RemoteProviderConfig c = new RemoteProviderConfig(HTTPS_URL, "/flags",
                "Authorization", "Bearer token", POS, POS, POLL, TTL, "agent");
        assertThat(c.authHeaderName()).isEqualTo("Authorization");
        assertThat(c.authHeaderValue()).isEqualTo("Bearer token");
    }

    @Test
    void invalidScheme_ftp_throws() {
        URI ftp = URI.create("ftp://flags.example.com");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RemoteProviderConfig(ftp, null, null, null, POS, POS, POLL, TTL, null))
                .withMessageContaining("ftp");
    }

    @Test
    void nullBaseUrl_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RemoteProviderConfig(null, null, null, null, POS, POS, POLL, TTL, null));
    }

    @Test
    void negativeConnectTimeout_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RemoteProviderConfig(HTTPS_URL, null, null, null,
                        Duration.ofSeconds(-1), POS, POLL, TTL, null));
    }

    @Test
    void zeroConnectTimeout_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RemoteProviderConfig(HTTPS_URL, null, null, null,
                        Duration.ZERO, POS, POLL, TTL, null));
    }

    @Test
    void negativeRequestTimeout_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RemoteProviderConfig(HTTPS_URL, null, null, null,
                        POS, Duration.ofSeconds(-1), POLL, TTL, null));
    }

    @Test
    void pollIntervalBelow5s_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RemoteProviderConfig(HTTPS_URL, null, null, null,
                        POS, POS, Duration.ofSeconds(4), Duration.ofMinutes(1), null));
    }

    @Test
    void cacheTtlLessThanPollInterval_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RemoteProviderConfig(HTTPS_URL, null, null, null,
                        POS, POS, Duration.ofSeconds(30), Duration.ofSeconds(10), null))
                .withMessageContaining("cacheTtl");
    }

    @Test
    void authHeaderNameWithoutValue_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RemoteProviderConfig(HTTPS_URL, null,
                        "Authorization", null, POS, POS, POLL, TTL, null));
    }

    @Test
    void authHeaderValueWithoutName_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RemoteProviderConfig(HTTPS_URL, null,
                        null, "Bearer token", POS, POS, POLL, TTL, null));
    }
}
