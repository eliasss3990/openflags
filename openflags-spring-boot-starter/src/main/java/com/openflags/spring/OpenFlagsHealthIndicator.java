package com.openflags.spring;

import com.openflags.core.OpenFlagsClient;
import com.openflags.core.provider.ProviderState;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator health indicator for openflags.
 * <p>
 * Reports {@link Health#up()} when the provider is in {@link ProviderState#READY} state,
 * {@link Health#down()} otherwise. Only active when {@code spring-boot-starter-actuator}
 * is on the classpath.
 * </p>
 */
@Component
@ConditionalOnClass(HealthIndicator.class)
public class OpenFlagsHealthIndicator implements HealthIndicator {

    private final OpenFlagsClient client;

    /**
     * Creates the health indicator.
     *
     * @param client the openflags client to check
     */
    public OpenFlagsHealthIndicator(OpenFlagsClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        ProviderState state = client.getProviderState();
        if (state == ProviderState.READY) {
            return Health.up().withDetail("provider.state", state).build();
        }
        return Health.down().withDetail("provider.state", state).build();
    }
}
