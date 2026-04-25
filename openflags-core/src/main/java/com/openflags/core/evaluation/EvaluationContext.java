package com.openflags.core.evaluation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Contextual information provided to the SDK when evaluating a feature flag.
 * <p>
 * In Fase 1 this holds only an optional targeting key (typically a user or session ID).
 * Fase 2 will extend it with user attributes for segmentation rules.
 * </p>
 * <p>
 * This is a final immutable class with a builder (not a record) because the builder
 * pattern with an optional targeting key and an attribute map does not fit naturally
 * in Java records (ADR-009).
 * </p>
 */
public final class EvaluationContext {

    private final String targetingKey;
    private final Map<String, Object> attributes;

    private EvaluationContext(String targetingKey, Map<String, Object> attributes) {
        this.targetingKey = targetingKey;
        this.attributes = Collections.unmodifiableMap(new HashMap<>(attributes));
    }

    /**
     * Creates an empty evaluation context with no targeting key and no attributes.
     *
     * @return an empty context
     */
    public static EvaluationContext empty() {
        return new EvaluationContext(null, Collections.emptyMap());
    }

    /**
     * Creates a context with the given targeting key and no attributes.
     *
     * @param targetingKey the targeting key (e.g., user ID); must not be null
     * @return a new context
     * @throws NullPointerException if targetingKey is null
     */
    public static EvaluationContext of(String targetingKey) {
        Objects.requireNonNull(targetingKey, "targetingKey must not be null");
        return new EvaluationContext(targetingKey, Collections.emptyMap());
    }

    /**
     * Creates a builder for constructing contexts with custom attributes.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the targeting key, if any.
     *
     * @return an optional containing the targeting key, or empty if none was set
     */
    public Optional<String> getTargetingKey() {
        return Optional.ofNullable(targetingKey);
    }

    /**
     * Returns the evaluation attributes as an unmodifiable map.
     *
     * @return the attributes; never null
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EvaluationContext other)) return false;
        return Objects.equals(targetingKey, other.targetingKey)
                && Objects.equals(attributes, other.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetingKey, attributes);
    }

    @Override
    public String toString() {
        return "EvaluationContext[targetingKey=" + targetingKey + ", attributes=" + attributes + "]";
    }

    /**
     * Builder for {@link EvaluationContext}.
     */
    public static final class Builder {

        private String targetingKey;
        private final Map<String, Object> attributes = new HashMap<>();

        private Builder() {}

        /**
         * Sets the targeting key.
         *
         * @param targetingKey the targeting key
         * @return this builder
         */
        public Builder targetingKey(String targetingKey) {
            this.targetingKey = targetingKey;
            return this;
        }

        /**
         * Adds a single attribute.
         *
         * @param key   the attribute name
         * @param value the attribute value
         * @return this builder
         */
        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        /**
         * Builds the {@link EvaluationContext}.
         *
         * @return a new immutable context
         */
        public EvaluationContext build() {
            return new EvaluationContext(targetingKey, attributes);
        }
    }
}
