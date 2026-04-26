package com.openflags.provider.remote;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class RemoteHttpClientTest {

    private WireMockServer wireMock;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    private RemoteProviderConfig config(String flagsPath) {
        return new RemoteProviderConfig(
                URI.create("http://localhost:" + wireMock.port()),
                flagsPath,
                null, null,
                TIMEOUT, TIMEOUT,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                "openflags-test/1.0"
        );
    }

    private RemoteProviderConfig configWithAuth(String headerName, String headerValue) {
        return new RemoteProviderConfig(
                URI.create("http://localhost:" + wireMock.port()),
                "/flags",
                headerName, headerValue,
                TIMEOUT, TIMEOUT,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                "openflags-test/1.0"
        );
    }

    @Test
    void fetch_200_returnsBody() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody("{\"flags\":{}}")));

        RemoteHttpClient client = new RemoteHttpClient(config("/flags"));
        HttpResponse<String> response = client.fetch();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"flags\":{}}");
    }

    @Test
    void fetch_401_propagatesStatus() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(401).withBody("Unauthorized")));

        RemoteHttpClient client = new RemoteHttpClient(config("/flags"));
        HttpResponse<String> response = client.fetch();

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void fetch_500_propagatesStatus() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

        RemoteHttpClient client = new RemoteHttpClient(config("/flags"));
        HttpResponse<String> response = client.fetch();

        assertThat(response.statusCode()).isEqualTo(500);
    }

    @Test
    void fetch_302_notFollowed() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", "http://evil.example.com")));

        RemoteHttpClient client = new RemoteHttpClient(config("/flags"));
        HttpResponse<String> response = client.fetch();

        // redirect should not be followed; status 302 returned directly
        assertThat(response.statusCode()).isEqualTo(302);
    }

    @Test
    void fetch_sendsUserAgentHeader() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody("{\"flags\":{}}")));

        RemoteHttpClient client = new RemoteHttpClient(config("/flags"));
        client.fetch();

        wireMock.verify(getRequestedFor(urlEqualTo("/flags"))
                .withHeader("User-Agent", equalTo("openflags-test/1.0")));
    }

    @Test
    void fetch_sendsAcceptHeader() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody("{\"flags\":{}}")));

        RemoteHttpClient client = new RemoteHttpClient(config("/flags"));
        client.fetch();

        wireMock.verify(getRequestedFor(urlEqualTo("/flags"))
                .withHeader("Accept", equalTo("application/json")));
    }

    @Test
    void fetch_sendsAuthorizationHeader() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody("{\"flags\":{}}")));

        RemoteHttpClient client = new RemoteHttpClient(configWithAuth("Authorization", "Bearer my-token"));
        client.fetch();

        wireMock.verify(getRequestedFor(urlEqualTo("/flags"))
                .withHeader("Authorization", equalTo("Bearer my-token")));
    }

    @Test
    void fetch_noAuthHeaderWhenNotConfigured() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody("{\"flags\":{}}")));

        RemoteHttpClient client = new RemoteHttpClient(config("/flags"));
        client.fetch();

        wireMock.verify(getRequestedFor(urlEqualTo("/flags"))
                .withoutHeader("Authorization"));
    }
}
