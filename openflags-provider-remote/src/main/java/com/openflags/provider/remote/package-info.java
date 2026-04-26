/**
 * Remote flag provider for openflags.
 * <p>
 * Provides a {@link com.openflags.provider.remote.RemoteFlagProvider} that fetches flag
 * definitions from a remote HTTP endpoint, caches them locally, and polls for updates
 * at a configurable interval. Supports resilience via stale-while-error policy.
 * </p>
 *
 * <h2>Quick start</h2>
 * <pre>
 * RemoteFlagProvider provider = RemoteFlagProviderBuilder
 *         .forUrl("https://flags.example.com")
 *         .pollInterval(Duration.ofSeconds(30))
 *         .cacheTtl(Duration.ofMinutes(5))
 *         .build();
 * provider.init();
 * </pre>
 */
package com.openflags.provider.remote;
