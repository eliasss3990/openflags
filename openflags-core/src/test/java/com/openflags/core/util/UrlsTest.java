package com.openflags.core.util;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlsTest {

    @Test
    void redact_returnsSameStringWhenNoUserInfo() {
        URI uri = URI.create("https://flags.example.com/api/v1");
        assertThat(Urls.redact(uri)).isEqualTo("https://flags.example.com/api/v1");
    }

    @Test
    void redact_replacesUserPasswordWithMask() {
        URI uri = URI.create("https://alice:s3cret@flags.example.com/api");
        String redacted = Urls.redact(uri);
        assertThat(redacted).doesNotContain("alice");
        assertThat(redacted).doesNotContain("s3cret");
        assertThat(redacted).contains("***@flags.example.com");
    }

    @Test
    void redact_preservesPortAndPath() {
        URI uri = URI.create("http://user:pass@host:8080/path?q=1#frag");
        String redacted = Urls.redact(uri);
        assertThat(redacted).contains(":8080");
        assertThat(redacted).contains("/path");
        assertThat(redacted).contains("q=1");
        assertThat(redacted).contains("#frag");
        assertThat(redacted).doesNotContain("user:pass");
    }

    @Test
    void redact_handlesIpv6Host() {
        URI uri = URI.create("http://user:pw@[::1]:8080/path");
        String redacted = Urls.redact(uri);
        assertThat(redacted).contains("[::1]").contains(":8080").contains("***");
        assertThat(redacted).doesNotContain("user:pw");
    }

    @Test
    void redact_handlesUserOnlyWithoutPassword() {
        URI uri = URI.create("http://alice@host/x");
        String redacted = Urls.redact(uri);
        assertThat(redacted).doesNotContain("alice").contains("***@host");
    }

    @Test
    void redact_keepsOpaqueUriAsIs() {
        URI uri = URI.create("urn:isbn:0451450523");
        assertThat(Urls.redact(uri)).isEqualTo("urn:isbn:0451450523");
    }

    @Test
    void redact_throwsOnNullUri() {
        assertThatThrownBy(() -> Urls.redact(null))
                .isInstanceOf(NullPointerException.class);
    }
}
