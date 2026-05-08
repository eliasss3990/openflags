/**
 * Checked and unchecked exceptions thrown across the public API:
 * {@link com.openflags.core.exception.OpenFlagsException} (base),
 * {@link com.openflags.core.exception.ProviderException} (provider lifecycle
 * and I/O failures), {@link com.openflags.core.exception.FlagNotFoundException}
 * and {@link com.openflags.core.exception.TypeMismatchException} (evaluation
 * errors surfaced via {@code EvaluationResult.reason}).
 *
 * @since 0.1.0
 */
package com.openflags.core.exception;
