/**
 * {@link com.openflags.core.provider.FlagProvider} SPI and supporting types:
 * {@link com.openflags.core.provider.ProviderState} (lifecycle status — see
 * ADR-002), {@link com.openflags.core.provider.ProviderDiagnostics} (per-provider
 * health/observability snapshot) and {@link com.openflags.core.provider.RemoteDefaults}
 * (shared default values surfaced by the remote provider and starter properties,
 * per ADR-009).
 *
 * @since 0.1.0
 */
package com.openflags.core.provider;
