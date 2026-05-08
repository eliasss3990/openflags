/**
 * Domain model for feature flags: {@link com.openflags.core.model.Flag} and its
 * type tags ({@link com.openflags.core.model.FlagType},
 * {@link com.openflags.core.model.FlagValue}). Providers emit instances of
 * these records; user code typically interacts with them through
 * {@code OpenFlagsClient} accessors rather than constructing them directly.
 *
 * @since 0.1.0
 */
package com.openflags.core.model;
