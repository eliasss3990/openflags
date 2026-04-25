package com.openflags.core.evaluation.rule;

/**
 * A single targeting or rollout rule attached to a {@link com.openflags.core.model.Flag}.
 * <p>
 * Sealed: only the implementations declared in {@code permits} are allowed.
 * Phase 2 ships {@link TargetingRule} and {@link SplitRule}; future phases may
 * extend this hierarchy (multi-variant splits, prerequisite rules, etc.) by
 * adding entries to the permits clause.
 * </p>
 *
 * <p>Implementations must be immutable and thread-safe.</p>
 */
public sealed interface Rule permits TargetingRule, SplitRule {

    /**
     * Returns a human-readable name for this rule, used in logs and debugging.
     * <p>The name is non-blank, set at parse time (e.g. {@code "early-adopters-AR"}).</p>
     *
     * @return the rule name; never null nor blank
     */
    String name();
}
