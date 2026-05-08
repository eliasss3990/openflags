/**
 * Public API root of OpenFlags core: the {@link com.openflags.core.OpenFlagsClient}
 * facade and its {@link com.openflags.core.OpenFlagsClientBuilder}, plus
 * supporting types ({@link com.openflags.core.OpenFlagsClientCustomizer},
 * {@link com.openflags.core.OpenFlagsMdc}). Evaluation inputs travel through
 * {@code com.openflags.core.evaluation.EvaluationContext}.
 *
 * <p>
 * Concrete {@code FlagProvider} implementations live in sibling modules
 * ({@code openflags-provider-file}, {@code openflags-provider-remote},
 * {@code openflags-provider-hybrid}); the SPI itself is in
 * {@link com.openflags.core.provider}.
 *
 * @since 0.1.0
 */
package com.openflags.core;
