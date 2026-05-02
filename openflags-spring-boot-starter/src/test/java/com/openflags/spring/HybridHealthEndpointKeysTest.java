package com.openflags.spring;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the keys documented in the README "Health endpoint" section against
 * the actual {@code HybridFlagProvider#diagnostics()} output exposed by the
 * Actuator health indicator. If a documented key is renamed or removed, this
 * test fails so the README is updated in the same change.
 */
class HybridHealthEndpointKeysTest {

    private static final String FLAGS_JSON = """
            {
              "flags": {
                "h": { "type": "boolean", "value": true, "enabled": true }
              }
            }
            """;

    private WireMockServer wiremock;

    @TempDir
    Path tempDir;

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
    void hybridHealth_exposesDocumentedKeys() {
        Path snapshot = tempDir.resolve("snap.json");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
                .withPropertyValues(
                        "openflags.provider=hybrid",
                        "openflags.remote.base-url=http://localhost:" + wiremock.port(),
                        "openflags.remote.poll-interval=5s",
                        "openflags.remote.cache-ttl=60s",
                        "openflags.hybrid.snapshot-path=" + snapshot.toAbsolutePath(),
                        "openflags.hybrid.watch-snapshot=false")
                .run(ctx -> {
                    OpenFlagsHealthIndicator indicator = ctx.getBean(OpenFlagsHealthIndicator.class);
                    Health health = indicator.health();
                    Map<String, Object> details = health.getDetails();

                    assertThat(details).containsEntry("provider.type", "hybrid");
                    assertThat(details).containsKey("provider.state");

                    // hybrid.* keys documented in README
                    assertThat(details).containsKeys(
                            "hybrid.routing_target",
                            "hybrid.snapshot_path",
                            "hybrid.snapshot_age_seconds",
                            "hybrid.last_snapshot_write");

                    // composed remote.* keys documented in README
                    assertThat(details).containsKeys(
                            "remote.base_url",
                            "remote.state",
                            "remote.poll_interval_ms",
                            "remote.cache_ttl_ms");

                    // composed file.* keys documented in README
                    assertThat(details).containsKeys(
                            "file.path",
                            "file.format",
                            "file.flag_count");
                });
    }
}
