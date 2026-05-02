package com.openflags.spring;

import com.openflags.core.OpenFlagsClient;
import com.openflags.core.provider.FlagProvider;
import com.openflags.core.provider.ProviderDiagnostics;
import com.openflags.core.provider.ProviderState;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Spring Boot Actuator health indicator for openflags.
 * <p>
 * Reports {@link Health#up()} when the provider is in
 * {@link ProviderState#READY},
 * {@link Health#outOfService()} when {@code DEGRADED} or {@code STALE} and
 * {@link Health#down()} otherwise. Only active when
 * {@code spring-boot-starter-actuator} is on the classpath.
 * </p>
 * <p>
 * When the configured {@link FlagProvider} implements
 * {@link ProviderDiagnostics}, the
 * indicator enriches the response with {@code provider.type} and the
 * provider-specific diagnostic map. The provider is injected as a separate bean
 * rather
 * than retrieved from {@link OpenFlagsClient}, which deliberately keeps the
 * provider
 * private.
 * </p>
 * <p>
 * This class is registered as a bean via
 * {@link OpenFlagsAutoConfiguration.ActuatorConfiguration}, which is
 * conditional on
 * Actuator being present in the classpath. No component scan is required.
 * </p>
 */
public class OpenFlagsHealthIndicator implements HealthIndicator {

    private final OpenFlagsClient client;
    private final FlagProvider provider;

    /**
     * Creates the health indicator.
     *
     * @param client   the openflags client to check
     * @param provider the active flag provider, or {@code null} when no
     *                 {@link FlagProvider} bean is available; in that case the
     *                 indicator reports only {@code provider.state}
     */
    public OpenFlagsHealthIndicator(OpenFlagsClient client, FlagProvider provider) {
        this.client = client;
        this.provider = provider;
    }

    @Override
    public Health health() {
        ProviderState state = client.getProviderState();
        Health.Builder builder = switch (state) {
            case READY -> Health.up();
            case DEGRADED, STALE -> Health.outOfService();
            default -> Health.down();
        };
        builder.withDetail("provider.state", state);
        if (provider instanceof ProviderDiagnostics diag) {
            builder.withDetail("provider.type", diag.providerType());
            diag.diagnostics().forEach(builder::withDetail);
        }
        return builder.build();
    }
}
