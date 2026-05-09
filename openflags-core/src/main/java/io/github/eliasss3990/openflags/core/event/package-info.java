/**
 * Change-notification SPI: {@link io.github.eliasss3990.openflags.core.event.FlagChangeEvent},
 * {@link io.github.eliasss3990.openflags.core.event.FlagChangeListener} and the
 * {@link io.github.eliasss3990.openflags.core.event.ChangeType} enum (including
 * {@code ENABLED}/{@code DISABLED} per ADR-005). Listeners registered via
 * {@code OpenFlagsClient.addChangeListener} receive events emitted by the
 * underlying provider after {@code init()} completes.
 *
 * @since 0.4.0
 */
package io.github.eliasss3990.openflags.core.event;
