/**
 * Spring Boot auto-configuration for OpenFlags: wires the selected
 * {@link io.github.eliasss3990.openflags.core.provider.FlagProvider} from
 * {@link io.github.eliasss3990.openflags.spring.OpenFlagsProperties} ({@code openflags.*}),
 * publishes an {@code OpenFlagsClient} bean and (optionally) Actuator health
 * and Micrometer metrics bindings.
 *
 * @since 0.6.0
 */
package io.github.eliasss3990.openflags.spring;
