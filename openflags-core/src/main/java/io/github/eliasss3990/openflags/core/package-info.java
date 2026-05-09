/**
 * Public API root of OpenFlags core: the {@link io.github.eliasss3990.openflags.core.OpenFlagsClient}
 * facade and its {@link io.github.eliasss3990.openflags.core.OpenFlagsClientBuilder}, plus
 * supporting types ({@link io.github.eliasss3990.openflags.core.OpenFlagsClientCustomizer},
 * {@link io.github.eliasss3990.openflags.core.OpenFlagsMdc}). Evaluation inputs travel through
 * {@code io.github.eliasss3990.openflags.core.evaluation.EvaluationContext}.
 *
 * <p>
 * Concrete {@code FlagProvider} implementations live in sibling modules
 * ({@code openflags-provider-file}, {@code openflags-provider-remote},
 * {@code openflags-provider-hybrid}); the SPI itself is in
 * {@link io.github.eliasss3990.openflags.core.provider}.
 *
 * @since 0.1.0
 */
package io.github.eliasss3990.openflags.core;
