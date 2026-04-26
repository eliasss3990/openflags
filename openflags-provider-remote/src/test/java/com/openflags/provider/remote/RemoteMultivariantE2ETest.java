package com.openflags.provider.remote;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.openflags.core.OpenFlagsClient;
import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationReason;
import com.openflags.core.evaluation.EvaluationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class RemoteMultivariantE2ETest {

    private static final String MULTIVARIANT_FLAGS_JSON = """
            {
              "flags": {
                "checkout-experiment": {
                  "type": "string",
                  "value": "control",
                  "enabled": true,
                  "rules": [
                    {
                      "name": "ab-test",
                      "kind": "multivariant",
                      "variants": [
                        { "value": "control",     "weight": 30 },
                        { "value": "treatment-a", "weight": 50 },
                        { "value": "treatment-b", "weight": 20 }
                      ]
                    }
                  ]
                }
              }
            }
            """;

    private WireMockServer wireMock;
    private RemoteFlagProvider provider;
    private OpenFlagsClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();

        wireMock.stubFor(get(urlEqualTo("/flags"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(MULTIVARIANT_FLAGS_JSON)));

        provider = RemoteFlagProviderBuilder
                .forUrl(URI.create("http://localhost:" + wireMock.port()))
                .pollInterval(Duration.ofSeconds(30))
                .cacheTtl(Duration.ofMinutes(5))
                .build();
        provider.init();

        client = OpenFlagsClient.builder().provider(provider).build();
    }

    @AfterEach
    void tearDown() {
        if (provider != null) provider.shutdown();
        wireMock.stop();
    }

    @Test
    void evaluationReason_isVariant_whenTargetingKeyPresent() {
        EvaluationContext ctx = EvaluationContext.of("user-abc");
        EvaluationResult<String> result = client.getStringResult("checkout-experiment", "default", ctx);

        assertThat(result.reason()).isEqualTo(EvaluationReason.VARIANT);
        assertThat(result.value()).isIn("control", "treatment-a", "treatment-b");
    }

    @Test
    void evaluationReason_isDefault_whenNoTargetingKey() {
        EvaluationContext ctx = EvaluationContext.empty();
        EvaluationResult<String> result = client.getStringResult("checkout-experiment", "default", ctx);

        assertThat(result.reason()).isEqualTo(EvaluationReason.DEFAULT);
        assertThat(result.value()).isEqualTo("control"); // flag's default value
    }

    @Test
    void distribution_10kTargetingKeys_withinOnePct() {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("control", 0);
        counts.put("treatment-a", 0);
        counts.put("treatment-b", 0);

        int total = 10_000;
        for (int i = 0; i < total; i++) {
            EvaluationContext ctx = EvaluationContext.of("user-" + i);
            EvaluationResult<String> result = client.getStringResult("checkout-experiment", "default", ctx);
            counts.merge(result.value(), 1, Integer::sum);
        }

        double pctControl    = (double) counts.get("control")     / total * 100;
        double pctTreatmentA = (double) counts.get("treatment-a") / total * 100;
        double pctTreatmentB = (double) counts.get("treatment-b") / total * 100;

        assertThat(pctControl).isBetween(29.0, 31.0);
        assertThat(pctTreatmentA).isBetween(49.0, 51.0);
        assertThat(pctTreatmentB).isBetween(19.0, 21.0);
    }

    @Test
    void determinism_sameTargetingKey_alwaysReturnsSameVariant() {
        EvaluationContext ctx = EvaluationContext.of("deterministic-user-123");
        String firstResult = client.getStringResult("checkout-experiment", "default", ctx).value();

        for (int i = 0; i < 100; i++) {
            String result = client.getStringResult("checkout-experiment", "default", ctx).value();
            assertThat(result).isEqualTo(firstResult);
        }
    }
}
