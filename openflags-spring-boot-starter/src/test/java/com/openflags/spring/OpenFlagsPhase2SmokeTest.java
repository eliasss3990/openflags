package com.openflags.spring;

import com.openflags.core.OpenFlagsClient;
import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationReason;
import com.openflags.core.evaluation.EvaluationResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests verifying that Phase 2 rule evaluation works end-to-end
 * when loaded via Spring Boot auto-configuration.
 */
class OpenFlagsPhase2SmokeTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenFlagsAutoConfiguration.class))
            .withPropertyValues(
                    "openflags.provider=file",
                    "openflags.file.path=classpath:flags-test-rules.yml",
                    "openflags.file.watch-enabled=false"
            );

    @Test
    void phase1Flag_noRules_returnsResolved() {
        contextRunner.run(ctx -> {
            OpenFlagsClient client = ctx.getBean(OpenFlagsClient.class);
            EvaluationResult<Boolean> result = client.getBooleanResult(
                    "simple-flag", false, EvaluationContext.empty());
            assertThat(result.value()).isTrue();
            assertThat(result.reason()).isEqualTo(EvaluationReason.RESOLVED);
        });
    }

    @Test
    void targetingRule_matchingContext_returnsTargetingMatch() {
        contextRunner.run(ctx -> {
            OpenFlagsClient client = ctx.getBean(OpenFlagsClient.class);
            EvaluationContext matchCtx = EvaluationContext.builder()
                    .attribute("country", "AR")
                    .build();
            EvaluationResult<Boolean> result = client.getBooleanResult("feature-country", false, matchCtx);
            assertThat(result.value()).isTrue();
            assertThat(result.reason()).isEqualTo(EvaluationReason.TARGETING_MATCH);
        });
    }

    @Test
    void targetingRule_nonMatchingContext_returnsDefault() {
        contextRunner.run(ctx -> {
            OpenFlagsClient client = ctx.getBean(OpenFlagsClient.class);
            EvaluationContext noMatchCtx = EvaluationContext.builder()
                    .attribute("country", "BR")
                    .build();
            EvaluationResult<Boolean> result = client.getBooleanResult("feature-country", false, noMatchCtx);
            assertThat(result.value()).isFalse();
            assertThat(result.reason()).isEqualTo(EvaluationReason.NO_RULE_MATCHED);
        });
    }

    @Test
    void splitRule_withTargetingKey_returnsSplit() {
        contextRunner.run(ctx -> {
            OpenFlagsClient client = ctx.getBean(OpenFlagsClient.class);
            EvaluationResult<Boolean> result = client.getBooleanResult(
                    "rollout-flag", false, EvaluationContext.of("user-123"));
            assertThat(result.value()).isTrue();
            assertThat(result.reason()).isEqualTo(EvaluationReason.SPLIT);
        });
    }

    @Test
    void splitRule_withoutTargetingKey_returnsDefault() {
        contextRunner.run(ctx -> {
            OpenFlagsClient client = ctx.getBean(OpenFlagsClient.class);
            EvaluationResult<Boolean> result = client.getBooleanResult(
                    "rollout-flag", false, EvaluationContext.empty());
            assertThat(result.value()).isFalse();
            assertThat(result.reason()).isEqualTo(EvaluationReason.NO_RULE_MATCHED);
        });
    }
}
