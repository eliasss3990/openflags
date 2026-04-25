package com.openflags.core.evaluation;

import com.openflags.core.exception.ProviderException;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import com.openflags.core.provider.FlagProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Internal engine that resolves feature flag values.
 * <p>
 * Stateless: all state lives in the {@link FlagProvider}. Each {@link #evaluate} call
 * is independent and thread-safe.
 * </p>
 *
 * <h3>Resolution order</h3>
 * <ol>
 *   <li>If the provider throws, return {@code defaultValue} with reason {@link EvaluationReason#PROVIDER_ERROR}.</li>
 *   <li>If the flag is not found, return {@code defaultValue} with reason {@link EvaluationReason#FLAG_NOT_FOUND}.</li>
 *   <li>If the flag is disabled, return {@code defaultValue} with reason {@link EvaluationReason#FLAG_DISABLED}.</li>
 *   <li>If the flag type does not match {@code expectedType}, return {@code defaultValue} with reason {@link EvaluationReason#TYPE_MISMATCH}.</li>
 *   <li>Otherwise, return the flag's value with reason {@link EvaluationReason#RESOLVED}.</li>
 * </ol>
 */
public final class FlagEvaluator {

    private static final Logger log = LoggerFactory.getLogger(FlagEvaluator.class);

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
     *
     * <p><strong>Phase 1 note:</strong> the {@code context} parameter is accepted but not used
     * for value resolution. It is reserved for targeting rules in Phase 2.
     * Passing {@link EvaluationContext#empty()} is valid and recommended.</p>
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

        T resolvedValue = extractTypedValue(flag.value(), expectedType);
        return new EvaluationResult<>(resolvedValue, EvaluationReason.RESOLVED, key);
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
