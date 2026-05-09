/**
 * Checked and unchecked exceptions thrown across the public API:
 * {@link io.github.eliasss3990.openflags.core.exception.OpenFlagsException} (base),
 * {@link io.github.eliasss3990.openflags.core.exception.ProviderException} (provider lifecycle
 * and I/O failures), {@link io.github.eliasss3990.openflags.core.exception.FlagNotFoundException}
 * and {@link io.github.eliasss3990.openflags.core.exception.TypeMismatchException} (evaluation
 * errors surfaced via {@code EvaluationResult.reason}).
 *
 * @since 0.1.0
 */
package io.github.eliasss3990.openflags.core.exception;
