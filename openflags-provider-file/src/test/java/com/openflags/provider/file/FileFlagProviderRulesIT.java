package com.openflags.provider.file;

import com.openflags.core.OpenFlagsClient;
import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationReason;
import com.openflags.core.evaluation.EvaluationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for Phase 2 rule evaluation via the file provider.
 */
class FileFlagProviderRulesIT {

    private OpenFlagsClient client;

    @BeforeEach
    void setUp() throws Exception {
        URI uri = getClass().getClassLoader().getResource("flags-e2e-rules.yml").toURI();
        Path path = Path.of(uri);
        FileFlagProvider provider = FileFlagProvider.builder().path(path).build();
        provider.init();
        client = OpenFlagsClient.builder().provider(provider).build();
    }

    @AfterEach
    void tearDown() {
        client.shutdown();
    }

    // Phase 1 compatibility: flag without rules returns RESOLVED

    @Test
    void phase1Flag_noRules_returnsResolved() {
        EvaluationResult<Boolean> result = client.getBooleanResult(
                "dark-mode", false, EvaluationContext.empty());
        assertThat(result.value()).isTrue();
        assertThat(result.reason()).isEqualTo(EvaluationReason.RESOLVED);
    }

    // TargetingRule: matching context

    @Test
    void targetingRule_matchingContext_returnsTargetingMatch() {
        EvaluationContext ctx = EvaluationContext.builder().attribute("country", "AR").build();
        EvaluationResult<Boolean> result = client.getBooleanResult("feature-x", false, ctx);
        assertThat(result.value()).isTrue();
        assertThat(result.reason()).isEqualTo(EvaluationReason.TARGETING_MATCH);
    }

    @Test
    void targetingRule_nonMatchingContext_returnsDefault() {
        EvaluationContext ctx = EvaluationContext.builder().attribute("country", "BR").build();
        EvaluationResult<Boolean> result = client.getBooleanResult("feature-x", false, ctx);
        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.NO_RULE_MATCHED);
    }

    @Test
    void targetingRule_emptyContext_returnsDefault() {
        EvaluationResult<Boolean> result = client.getBooleanResult(
                "feature-x", false, EvaluationContext.empty());
        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.NO_RULE_MATCHED);
    }

    // SplitRule: 100% always matches when targetingKey is present

    @Test
    void splitRule_100percent_withTargetingKey_returnsSplit() {
        EvaluationResult<Boolean> result = client.getBooleanResult(
                "new-checkout", false, EvaluationContext.of("user-abc"));
        assertThat(result.value()).isTrue();
        assertThat(result.reason()).isEqualTo(EvaluationReason.SPLIT);
    }

    @Test
    void splitRule_100percent_withoutTargetingKey_returnsDefault() {
        EvaluationResult<Boolean> result = client.getBooleanResult(
                "new-checkout", false, EvaluationContext.empty());
        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.NO_RULE_MATCHED);
    }

    @Test
    void splitRule_0percent_neverMatches() {
        EvaluationResult<Boolean> result = client.getBooleanResult(
                "new-checkout-off", false, EvaluationContext.of("user-abc"));
        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.NO_RULE_MATCHED);
    }

    // First-match-wins with multi-rule string flag

    @Test
    void multiRuleFlag_firstRuleMatches_returnsFirstMatch() {
        EvaluationContext ctx = EvaluationContext.builder()
                .attribute("email", "alice@openflags.com")
                .attribute("plan", "pro")
                .build();
        EvaluationResult<String> result = client.getStringResult("premium-banner", "def", ctx);
        assertThat(result.value()).isEqualTo("internal-banner");
        assertThat(result.reason()).isEqualTo(EvaluationReason.TARGETING_MATCH);
    }

    @Test
    void multiRuleFlag_secondRuleMatches_returnsSecondMatch() {
        EvaluationContext ctx = EvaluationContext.builder()
                .attribute("email", "alice@external.com")
                .attribute("plan", "enterprise")
                .build();
        EvaluationResult<String> result = client.getStringResult("premium-banner", "def", ctx);
        assertThat(result.value()).isEqualTo("premium-banner");
        assertThat(result.reason()).isEqualTo(EvaluationReason.TARGETING_MATCH);
    }

    @Test
    void multiRuleFlag_noRuleMatches_returnsDefault() {
        EvaluationContext ctx = EvaluationContext.builder()
                .attribute("plan", "free")
                .build();
        EvaluationResult<String> result = client.getStringResult("premium-banner", "def", ctx);
        assertThat(result.value()).isEqualTo("default-banner");
        assertThat(result.reason()).isEqualTo(EvaluationReason.NO_RULE_MATCHED);
    }

    // Disabled flag ignores rules

    @Test
    void disabledFlag_withMatchingContext_returnsDisabled() {
        EvaluationContext ctx = EvaluationContext.builder().attribute("x", "y").build();
        EvaluationResult<Boolean> result = client.getBooleanResult("old-feature", false, ctx);
        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.FLAG_DISABLED);
    }
}
