package com.openflags.spring;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.openflags.core.OpenFlagsClient;
import com.openflags.core.provider.FlagProvider;
import com.openflags.provider.hybrid.HybridFlagProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class OpenFlagsHybridAutoConfigurationTest {

    private static final String FLAGS_JSON = """
            {
              "flags": {
                "hybrid-flag": {
                  "type": "boolean",
                  "value": true,
                  "enabled": true
                }
              }
            }
            """;

    private WireMockServer wiremock;

    @TempDir
    Path tempDir;

    @BeforeEach
    void startWireMock() {
        wiremock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wiremock.start();
        wiremock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200).withBody(FLAGS_JSON)
                        .withHeader("Content-Type", "application/json")));
    }

    @AfterEach
    void stopWireMock() {
        wiremock.stop();
    }

    @Test
    void hybridProvider_createdWhenConfigured() {
        Path snapshot = tempDir.resolve("snap.json");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
                .withPropertyValues(
                        "openflags.provider=hybrid",
                        "openflags.remote.base-url=http://localhost:" + wiremock.port(),
                        "openflags.remote.poll-interval=5s",
                        "openflags.remote.cache-ttl=60s",
                        "openflags.hybrid.snapshot-path=" + snapshot.toAbsolutePath(),
                        "openflags.hybrid.watch-snapshot=false"
                )
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(HybridFlagProvider.class);
                    assertThat(ctx).hasSingleBean(OpenFlagsClient.class);
                });
    }

    @Test
    void fileProvider_stillWorksWith_fileProvider() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
                .withPropertyValues(
                        "openflags.provider=file",
                        "openflags.file.path=classpath:flags-test.yml",
                        "openflags.file.watch-enabled=false"
                )
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(HybridFlagProvider.class);
                    assertThat(ctx).hasSingleBean(FlagProvider.class);
                });
    }

    @Test
    void missingBaseUrl_failsWithClearMessage() {
        Path snapshot = tempDir.resolve("snap.json");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
                .withPropertyValues(
                        "openflags.provider=hybrid",
                        "openflags.hybrid.snapshot-path=" + snapshot.toAbsolutePath(),
                        "openflags.hybrid.watch-snapshot=false"
                )
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure().getMessage())
                            .contains("base-url");
                });
    }

    @Test
    void missingSnapshotPath_failsWithClearMessage() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
                .withPropertyValues(
                        "openflags.provider=hybrid",
                        "openflags.remote.base-url=http://localhost:" + wiremock.port(),
                        "openflags.remote.poll-interval=5s",
                        "openflags.remote.cache-ttl=60s"
                )
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure().getMessage())
                            .contains("snapshot-path");
                });
    }
}
