package com.openflags.core;

import com.openflags.core.evaluation.FlagEvaluator;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.provider.FlagProvider;

/**
 * Builder for {@link OpenFlagsClient}.
 * <p>
 * Requires a {@link FlagProvider}; all other settings are optional.
 * </p>
 *
 * <pre>
 * OpenFlagsClient client = OpenFlagsClient.builder()
 *     .provider(myProvider)
 *     .build();
 * </pre>
 */
public final class OpenFlagsClientBuilder {

    private FlagProvider provider;

    OpenFlagsClientBuilder() {}

    /**
     * Sets the flag provider (required).
     *
     * @param provider the provider to use; must not be null
     * @return this builder
     * @throws NullPointerException if provider is null
     */
    public OpenFlagsClientBuilder provider(FlagProvider provider) {
        if (provider == null) {
            throw new NullPointerException("provider must not be null");
        }
        this.provider = provider;
        return this;
    }

    /**
     * Builds the {@link OpenFlagsClient}.
     * <p>
     * Calls {@link FlagProvider#init()} on the provider before returning. Since {@code init()}
     * is idempotent, calling it on an already-initialized provider is safe.
     * </p>
     *
     * @return a ready-to-use client
     * @throws IllegalStateException if no provider was set
     * @throws ProviderException     if provider initialization fails
     */
    public OpenFlagsClient build() {
        if (provider == null) {
            throw new IllegalStateException("A FlagProvider must be set before building the client");
        }
        provider.init();
        return new OpenFlagsClient(provider, new FlagEvaluator());
    }
}
