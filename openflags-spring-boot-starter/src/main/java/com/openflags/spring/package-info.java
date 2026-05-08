/**
 * Spring Boot auto-configuration for OpenFlags: wires the selected
 * {@link com.openflags.core.provider.FlagProvider} from
 * {@link com.openflags.spring.OpenFlagsProperties} ({@code openflags.*}),
 * publishes an {@code OpenFlagsClient} bean and (optionally) Actuator health
 * and Micrometer metrics bindings.
 *
 * @since 0.6.0
 */
package com.openflags.spring;
