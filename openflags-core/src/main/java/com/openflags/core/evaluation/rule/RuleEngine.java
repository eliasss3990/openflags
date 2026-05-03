package com.openflags.core.evaluation.rule;

import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.core.evaluation.EvaluationReason;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

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
     * Outcome of a rule resolution.
     *
     * @param value         the resolved {@link FlagValue}
     * @param reason        the {@link EvaluationReason}: one of
     *                      {@link EvaluationReason#RESOLVED}, {@link EvaluationReason#TARGETING_MATCH},
     *                      {@link EvaluationReason#SPLIT}, {@link EvaluationReason#VARIANT},
     *                      or {@link EvaluationReason#NO_RULE_MATCHED}
     * @param variant       the variant label when a {@code MultiVariantRule} matched, otherwise {@code null}
     * @param matchedRuleId the {@link Rule#name()} of the rule that matched, or {@code null} when no rule matched
     */
    public record Resolution(FlagValue value, EvaluationReason reason, String variant, String matchedRuleId) {}

    /**
     * Resolves a flag's value using its rules and the provided context.
     * <ul>
     *   <li>If the flag has no rules, returns the default value with reason {@code RESOLVED}
     *       (preserves Phase 1 behavior).</li>
     *   <li>If a rule matches, returns its value with reason {@code TARGETING_MATCH} or
     *       {@code SPLIT}.</li>
     *   <li>If no rule matches, returns the default value with reason {@code NO_RULE_MATCHED}.</li>
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
            return new Resolution(flag.value(), EvaluationReason.RESOLVED, null, null);
        }

        for (Rule rule : flag.rules()) {
            Optional<Resolution> result = evaluateRule(rule, flag, context);
            if (result.isPresent()) {
                log.debug("Flag '{}' matched rule '{}' with reason {}", flag.key(), rule.name(),
                        result.get().reason());
                return result.get();
            }
        }

        log.debug("Flag '{}' has rules but none matched; using default value", flag.key());
        return new Resolution(flag.value(), EvaluationReason.NO_RULE_MATCHED, null, null);
    }

    private Optional<Resolution> evaluateRule(Rule rule, Flag flag, EvaluationContext context) {
        return switch (rule) {
            case TargetingRule tr     -> evaluateTargetingRule(tr, context);
            case SplitRule sr         -> evaluateSplitRule(sr, flag.key(), context);
            case MultiVariantRule mvr -> evaluateMultiVariantRule(mvr, flag.key(), context);
        };
    }

    private Optional<Resolution> evaluateMultiVariantRule(MultiVariantRule rule, String flagKey,
            EvaluationContext context) {
        return context.getTargetingKey().map(tk -> {
            int bucket = BucketAllocator.bucket(flagKey, tk);
            WeightedVariant chosen = VariantSelector.select(rule.variants(), bucket);
            return new Resolution(chosen.value(), EvaluationReason.VARIANT,
                    toVariantLabel(chosen.value()), rule.name());
        });
    }

    private Optional<Resolution> evaluateTargetingRule(TargetingRule rule, EvaluationContext context) {
        boolean allMatch = rule.conditions().stream()
                .allMatch(c -> ConditionEvaluator.matches(c, context));
        return allMatch
                ? Optional.of(new Resolution(rule.value(), EvaluationReason.TARGETING_MATCH, null, rule.name()))
                : Optional.empty();
    }

    private Optional<Resolution> evaluateSplitRule(SplitRule rule, String flagKey, EvaluationContext context) {
        return context.getTargetingKey()
                .flatMap(tk -> {
                    int bucket = BucketAllocator.bucket(flagKey, tk);
                    return bucket < rule.percentage()
                            ? Optional.of(new Resolution(rule.value(), EvaluationReason.SPLIT, null, rule.name()))
                            : Optional.empty();
                });
    }

    /**
     * Converts a variant's {@link FlagValue} to a human-readable label for the
     * {@code variant} field of {@link com.openflags.core.evaluation.EvaluationResult}.
     * String flags return their natural value; numeric and boolean flags are stringified;
     * object flags return {@code null} (no compact label is meaningful).
     */
    private static String toVariantLabel(FlagValue value) {
        return switch (value.getType()) {
            case STRING  -> value.asString();
            case BOOLEAN -> String.valueOf(value.asBoolean());
            case NUMBER  -> {
                double d = value.asNumber();
                if (!Double.isFinite(d)) yield null;
                // Omit the decimal part for whole numbers (e.g., "50" instead of "50.0").
                // Guard against doubles outside long range: (long) cast saturates silently.
                if (d == Math.floor(d) && d >= Long.MIN_VALUE && d < Long.MAX_VALUE) {
                    yield String.valueOf((long) d);
                }
                yield String.valueOf(d);
            }
            case OBJECT  -> null;
        };
    }
}
