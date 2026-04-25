/**
 * Rule engine for conditional flag evaluation.
 *
 * <p>This package implements the Phase 2 targeting and rollout engine embedded directly in
 * {@code openflags-core}, with zero additional Maven dependencies.</p>
 *
 * <h2>Core abstractions</h2>
 * <ul>
 *   <li>{@link com.openflags.core.evaluation.rule.Rule} — sealed interface; only
 *       {@link com.openflags.core.evaluation.rule.TargetingRule} and
 *       {@link com.openflags.core.evaluation.rule.SplitRule} are permitted in Phase 2.</li>
 *   <li>{@link com.openflags.core.evaluation.rule.RuleEngine} — evaluates an ordered list of
 *       rules against an {@link com.openflags.core.evaluation.EvaluationContext} using
 *       <em>first-match-wins</em> semantics.</li>
 *   <li>{@link com.openflags.core.evaluation.rule.ConditionEvaluator} — applies a single
 *       {@link com.openflags.core.evaluation.rule.Condition} to the context attributes.</li>
 *   <li>{@link com.openflags.core.evaluation.rule.Operator} — the 12 supported comparison
 *       operators: {@code EQ}, {@code NOT_EQ}, {@code IN}, {@code NOT_IN}, {@code GT},
 *       {@code GTE}, {@code LT}, {@code LTE}, {@code CONTAINS}, {@code NOT_CONTAINS},
 *       {@code MATCHES}, {@code NOT_MATCHES}.</li>
 *   <li>{@link com.openflags.core.evaluation.rule.BucketAllocator} — assigns a user to a
 *       bucket (0–99) via {@link com.openflags.core.evaluation.rule.MurmurHash3} for
 *       consistent percentage rollout.</li>
 * </ul>
 *
 * <h2>Evaluation flow</h2>
 * <ol>
 *   <li>{@code FlagEvaluator} delegates to {@code RuleEngine.evaluate()} after the standard
 *       enabled/type checks pass.</li>
 *   <li>{@code RuleEngine} iterates rules in declaration order and returns the first match.</li>
 *   <li>If no rule matches, {@code FlagEvaluator} falls back to the flag's static value with
 *       reason {@code DEFAULT}.</li>
 *   <li>Flags without a {@code rules:} section are unaffected (reason {@code RESOLVED}).</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * <p>All classes in this package are immutable and stateless after construction. They are safe
 * for concurrent use without external synchronization.</p>
 */
package com.openflags.core.evaluation.rule;
