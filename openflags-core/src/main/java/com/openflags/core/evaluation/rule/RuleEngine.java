package com.openflags.core.evaluation.rule;

import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationReason;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Resolves the value of a flag taking its rules into account.
 * <p>
 * Stateless and thread-safe. The resolution order follows the order of declaration
 * in {@link Flag#rules()} and is first-match-wins.
 * </p>
 */
public final class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    /**
     * Outcome of a rule resolution: the value to use and the reason that explains it.
     *
     * @param value  the resolved {@link FlagValue}
     * @param reason the {@link EvaluationReason}: one of
     *               {@link EvaluationReason#RESOLVED}, {@link EvaluationReason#TARGETING_MATCH},
     *               {@link EvaluationReason#SPLIT}, or {@link EvaluationReason#DEFAULT}
     */
    public record Resolution(FlagValue value, EvaluationReason reason) {}

    /**
     * Resolves a flag's value using its rules and the provided context.
     * <ul>
     *   <li>If the flag has no rules, returns the default value with reason {@code RESOLVED}
     *       (preserves Phase 1 behavior).</li>
     *   <li>If a rule matches, returns its value with reason {@code TARGETING_MATCH} or
     *       {@code SPLIT}.</li>
     *   <li>If no rule matches, returns the default value with reason {@code DEFAULT}.</li>
     * </ul>
     *
     * @param flag    the flag to resolve; must not be null
     * @param context the evaluation context; must not be null
     * @return the resolution; never null
     * @throws NullPointerException if any argument is null
     */
    public Resolution resolve(Flag flag, EvaluationContext context) {
        Objects.requireNonNull(flag, "flag must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (flag.rules().isEmpty()) {
            return new Resolution(flag.value(), EvaluationReason.RESOLVED);
        }

        for (Rule rule : flag.rules()) {
            Resolution resolution = evaluateRule(rule, flag, context);
            if (resolution != null) {
                log.debug("Flag '{}' matched rule '{}' with reason {}", flag.key(), rule.name(),
                        resolution.reason());
                return resolution;
            }
        }

        log.debug("Flag '{}' has rules but none matched; using default value", flag.key());
        return new Resolution(flag.value(), EvaluationReason.DEFAULT);
    }

    private Resolution evaluateRule(Rule rule, Flag flag, EvaluationContext context) {
        if (rule instanceof TargetingRule tr) return evaluateTargetingRule(tr, context);
        if (rule instanceof SplitRule sr) return evaluateSplitRule(sr, flag.key(), context);
        return null;
    }

    private Resolution evaluateTargetingRule(TargetingRule rule, EvaluationContext context) {
        boolean allMatch = rule.conditions().stream()
                .allMatch(c -> ConditionEvaluator.matches(c, context));
        return allMatch ? new Resolution(rule.value(), EvaluationReason.TARGETING_MATCH) : null;
    }

    private Resolution evaluateSplitRule(SplitRule rule, String flagKey, EvaluationContext context) {
        return context.getTargetingKey()
                .map(tk -> {
                    int bucket = BucketAllocator.bucket(flagKey, tk);
                    return bucket < rule.percentage()
                            ? new Resolution(rule.value(), EvaluationReason.SPLIT)
                            : null;
                })
                .orElse(null);
    }
}
