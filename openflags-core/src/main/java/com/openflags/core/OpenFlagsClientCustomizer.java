package com.openflags.core;

/**
 * Hook for infrastructure code (e.g. the Spring Boot starter) to extend
 * an {@link OpenFlagsClientBuilder} just before
 * {@link OpenFlagsClientBuilder#build()}.
 *
 * <p>
 * All beans of this type discovered by the auto-configuration are
 * applied in {@code @Order} order before the client is built. Use it to
 * register evaluation listeners, attach a metrics registry, toggle MDC,
 * or apply any other builder option without owning the bean factory.
 *
 * <p>
 * Implementations MUST be idempotent and side-effect free except for
 * the mutations they apply to the supplied builder.
 *
 * @since 0.5.0
 */
@FunctionalInterface
public interface OpenFlagsClientCustomizer {

    /**
     * Mutates the supplied builder. Called once per client construction.
     *
     * @param builder the builder being assembled, never {@code null}
     */
    void customize(OpenFlagsClientBuilder builder);
}
