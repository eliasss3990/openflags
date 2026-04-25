package com.openflags.core.evaluation;

import com.openflags.core.evaluation.rule.RuleEngine;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import com.openflags.core.provider.FlagProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Internal engine that resolves feature flag values.
 * <p>
 * Stateless: all state lives in the {@link FlagProvider}. Each {@link #evaluate} call
 * is independent and thread-safe.
 * </p>
 *
 * <h2>Resolution order</h2>
 * <ol>
 *   <li>If the provider throws, return {@code defaultValue} with reason {@link EvaluationReason#PROVIDER_ERROR}.</li>
 *   <li>If the flag is not found, return {@code defaultValue} with reason {@link EvaluationReason#FLAG_NOT_FOUND}.</li>
 *   <li>If the flag is disabled, return {@code defaultValue} with reason {@link EvaluationReason#FLAG_DISABLED}.</li>
 *   <li>If the flag type does not match {@code expectedType}, return {@code defaultValue} with reason {@link EvaluationReason#TYPE_MISMATCH}.</li>
 *   <li>Otherwise, delegate to {@link RuleEngine} which returns one of:
 *       {@link EvaluationReason#RESOLVED}, {@link EvaluationReason#TARGETING_MATCH},
 *       {@link EvaluationReason#SPLIT}, or {@link EvaluationReason#DEFAULT}.</li>
 * </ol>
 */
public final class FlagEvaluator {

    private static final Logger log = LoggerFactory.getLogger(FlagEvaluator.class);

    private final RuleEngine ruleEngine;

    /**
     * Default constructor; uses a fresh {@link RuleEngine}.
     */
    public FlagEvaluator() {
        this(new RuleEngine());
    }

    /**
     * Test-friendly constructor that injects a {@link RuleEngine}.
     *
     * @param ruleEngine the engine; must not be null
     */
    public FlagEvaluator(RuleEngine ruleEngine) {
        this.ruleEngine = Objects.requireNonNull(ruleEngine, "ruleEngine must not be null");
    }

    /**
     * Evaluates a flag and returns a typed result.
     *
     * @param provider     the flag provider to query; must not be null
     * @param key          the flag key; must not be null
     * @param expectedType the expected Java type of the resolved value
     * @param defaultValue the fallback value used when the flag is not found, disabled, or mismatched
     * @param context      evaluation context; must not be null
     * @param <T>          the value type
     * @return an evaluation result; never null
     */
    public <T> EvaluationResult<T> evaluate(
            FlagProvider provider,
            String key,
            Class<T> expectedType,
            T defaultValue,
            EvaluationContext context) {

        Optional<Flag> flagOpt;
        try {
            flagOpt = provider.getFlag(key);
        } catch (ProviderException e) {
            log.warn("Provider error evaluating flag '{}': {}", key, e.getMessage());
            return new EvaluationResult<>(defaultValue, EvaluationReason.PROVIDER_ERROR, key);
        } catch (Exception e) {
            log.warn("Unexpected error evaluating flag '{}': {}", key, e.getMessage());
            return new EvaluationResult<>(defaultValue, EvaluationReason.PROVIDER_ERROR, key);
        }

        if (flagOpt.isEmpty()) {
            return new EvaluationResult<>(defaultValue, EvaluationReason.FLAG_NOT_FOUND, key);
        }

        Flag flag = flagOpt.get();

        if (!flag.enabled()) {
            return new EvaluationResult<>(defaultValue, EvaluationReason.FLAG_DISABLED, key);
        }

        FlagType expectedFlagType = toFlagType(expectedType);
        if (expectedFlagType == null || flag.type() != expectedFlagType) {
            log.debug("Type mismatch for flag '{}': expected {}, got {}", key, expectedFlagType, flag.type());
            return new EvaluationResult<>(defaultValue, EvaluationReason.TYPE_MISMATCH, key);
        }

        RuleEngine.Resolution resolution = ruleEngine.resolve(flag, context);
        T resolvedValue = extractTypedValue(resolution.value(), expectedType);
        return new EvaluationResult<>(resolvedValue, resolution.reason(), key);
    }

    private static FlagType toFlagType(Class<?> javaType) {
        if (javaType == Boolean.class || javaType == boolean.class) return FlagType.BOOLEAN;
        if (javaType == String.class) return FlagType.STRING;
        if (javaType == Double.class || javaType == double.class) return FlagType.NUMBER;
        if (Map.class.isAssignableFrom(javaType)) return FlagType.OBJECT;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T extractTypedValue(FlagValue flagValue, Class<T> expectedType) {
        if (expectedType == Boolean.class || expectedType == boolean.class) {
            return (T) Boolean.valueOf(flagValue.asBoolean());
        }
        if (expectedType == String.class) {
            return (T) flagValue.asString();
        }
        if (expectedType == Double.class || expectedType == double.class) {
            return (T) Double.valueOf(flagValue.asNumber());
        }
        if (Map.class.isAssignableFrom(expectedType)) {
            return (T) flagValue.asObject();
        }
        throw new IllegalStateException("Unsupported expectedType: " + expectedType);
    }
}
