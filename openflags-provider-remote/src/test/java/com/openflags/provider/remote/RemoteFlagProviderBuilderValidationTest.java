package com.openflags.provider.remote;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RemoteFlagProviderBuilderValidationTest {

    private static final String BASE_URL = "https://flags.example.com";

    // ── bearerToken ────────────────────────────────────────────────────────

    @Test
    void bearerToken_null_throwsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> RemoteFlagProviderBuilder.forUrl(BASE_URL).bearerToken(null))
                .withMessageContaining("bearerToken");
    }

    @Test
    void bearerToken_blank_throwsIAE() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RemoteFlagProviderBuilder.forUrl(BASE_URL).bearerToken("   "))
                .withMessageContaining("bearerToken");
    }

    @Test
    void bearerToken_empty_throwsIAE() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RemoteFlagProviderBuilder.forUrl(BASE_URL).bearerToken(""))
                .withMessageContaining("bearerToken");
    }

    @Test
    void bearerToken_valid_buildsSuccessfully() {
        assertThatNoException().isThrownBy(() ->
                RemoteFlagProviderBuilder.forUrl(BASE_URL).bearerToken("my-secret-token").build());
    }

    // ── apiKey ─────────────────────────────────────────────────────────────

    @Test
    void apiKey_nullHeaderName_throwsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> RemoteFlagProviderBuilder.forUrl(BASE_URL).apiKey(null, "value"))
                .withMessageContaining("headerName");
    }

    @Test
    void apiKey_nullValue_throwsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> RemoteFlagProviderBuilder.forUrl(BASE_URL).apiKey("X-API-Key", null))
                .withMessageContaining("value");
    }

    @Test
    void apiKey_blankHeaderName_throwsIAE() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RemoteFlagProviderBuilder.forUrl(BASE_URL).apiKey("  ", "v"))
                .withMessageContaining("headerName");
    }

    @Test
    void apiKey_blankValue_throwsIAE() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RemoteFlagProviderBuilder.forUrl(BASE_URL).apiKey("X-API-Key", ""))
                .withMessageContaining("apiKey value");
    }

    @Test
    void apiKey_valid_buildsSuccessfully() {
        assertThatNoException().isThrownBy(() ->
                RemoteFlagProviderBuilder.forUrl(BASE_URL).apiKey("X-API-Key", "secret").build());
    }
}
