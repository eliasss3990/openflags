package com.openflags.spring;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.openflags.provider.remote.RemoteFlagProvider;
import com.openflags.provider.remote.RemoteProviderConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Field;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring guard (G1): non-default values configured via
 * {@code openflags.remote.*} properties must reach the
 * {@link RemoteProviderConfig} held by the {@link RemoteFlagProvider} bean
 * built by {@link OpenFlagsAutoConfiguration}.
 *
 * <p>If a setter is silently dropped from
 * {@code RemoteProviderConfiguration#remoteFlagProvider}, this test fails. Uses
 * reflection on the package-private {@code config} field to avoid widening the
 * production API surface (decision D-9 = A in the post-review plan).
 */
class RemotePropertiesWiringTest {

    private static final String FLAGS_JSON = """
            { "flags": { "x": { "type": "boolean", "value": true, "enabled": true } } }
            """;

    private WireMockServer wiremock;

    @BeforeEach
    void start() {
        wiremock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wiremock.start();
        wiremock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody(FLAGS_JSON)
                        .withHeader("Content-Type", "application/json")));
    }

    @AfterEach
    void stop() {
        wiremock.stop();
    }

    @Test
    void remoteProperties_propagateToRemoteProviderConfig() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
                .withPropertyValues(
                        "openflags.provider=remote",
                        "openflags.remote.base-url=http://localhost:" + wiremock.port(),
                        "openflags.remote.connect-timeout=2s",
                        "openflags.remote.request-timeout=3s",
                        "openflags.remote.poll-interval=7s",
                        "openflags.remote.cache-ttl=30s",
                        "openflags.remote.failure-threshold=7",
                        "openflags.remote.max-backoff=2m")
                .run(ctx -> {
                    RemoteFlagProvider provider = ctx.getBean(RemoteFlagProvider.class);
                    RemoteProviderConfig cfg = readConfig(provider);

                    assertThat(cfg.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(cfg.requestTimeout()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(cfg.pollInterval()).isEqualTo(Duration.ofSeconds(7));
                    assertThat(cfg.cacheTtl()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(cfg.failureThreshold()).isEqualTo(7);
                    assertThat(cfg.maxBackoff()).isEqualTo(Duration.ofMinutes(2));
                });
    }

    private static RemoteProviderConfig readConfig(RemoteFlagProvider provider) {
        try {
            Field f = RemoteFlagProvider.class.getDeclaredField("config");
            f.setAccessible(true);
            return (RemoteProviderConfig) f.get(provider);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError(
                    "Could not read RemoteFlagProvider#config via reflection — has the"
                            + " field been renamed or removed? Update the test or the"
                            + " production field name to match.", e);
        }
    }
}
