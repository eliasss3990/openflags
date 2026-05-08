/**
 * Change-notification SPI: {@link com.openflags.core.event.FlagChangeEvent},
 * {@link com.openflags.core.event.FlagChangeListener} and the
 * {@link com.openflags.core.event.ChangeType} enum (including
 * {@code ENABLED}/{@code DISABLED} per ADR-005). Listeners registered via
 * {@code OpenFlagsClient.addChangeListener} receive events emitted by the
 * underlying provider after {@code init()} completes.
 *
 * @since 0.4.0
 */
package com.openflags.core.event;
