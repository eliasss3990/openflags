package com.openflags.spring;

import com.openflags.core.OpenFlagsClientCustomizer;
import com.openflags.core.metrics.MetricsRecorder;
import com.openflags.core.metrics.MicrometerMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Conditional Micrometer bindings for the openflags Spring Boot starter.
 * <p>
 * This configuration is loaded only when
 * {@code io.micrometer.core.instrument.MeterRegistry}
 * is on the classpath and the property {@code openflags.metrics.enabled} is
 * {@code true}
 * (the default). It contributes:
 * </p>
 * <ul>
 * <li>A shared {@link MetricsRecorder} bean built against the active
 * {@link MeterRegistry}. {@code openflags.metrics.tag-flag-key} controls
 * whether the {@code flag} and {@code variant} tags are attached.</li>
 * <li>An {@link OpenFlagsClientCustomizer} that injects the same
 * {@link MetricsRecorder} bean into the {@code OpenFlagsClient} builder via
 * {@code metricsRecorder(...)}, avoiding the reflective
 * {@code metricsRegistry(...)} path and a duplicate recorder instance.</li>
 * <li>An optional {@link MeterFilter} bean exposing the static tags configured
 * under {@code openflags.metrics.tags.*} as common tags on the registry.</li>
 * </ul>
 * <p>
 * Package-private on purpose: this class is an implementation detail of
 * {@link OpenFlagsAutoConfiguration} and is not part of the public API surface.
 * </p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "openflags.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
class MicrometerBindings {

    /**
     * Customizer that wires the shared {@link MetricsRecorder} bean into the
     * openflags client builder. Reusing the same recorder avoids creating a
     * second {@code MicrometerMetricsRecorder} via the reflective
     * {@code metricsRegistry(...)} path and keeps a single counter cache.
     *
     * @param recorder the metrics recorder bean
     * @return a customizer that binds the recorder into the client builder
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    OpenFlagsClientCustomizer openflagsMicrometerCustomizer(MetricsRecorder recorder) {
        return builder -> builder.metricsRecorder(recorder);
    }

    /**
     * Exposes a {@link MetricsRecorder} bean wired against the active
     * {@link MeterRegistry}. {@link OpenFlagsAutoConfiguration} injects this
     * bean (via {@code ObjectProvider}) into the provider factory methods to
     * wire poll listeners, snapshot-write metrics and hybrid fallback metrics. The recorder shares the same {@link MeterRegistry} as
     * the one wired into {@link com.openflags.core.OpenFlagsClient}, so all
     * meters end up in a single registry.
     *
     * @param registry the active meter registry
     * @param props    the openflags properties; used to read
     *                 {@code metrics.tag-flag-key}
     * @return a Micrometer-backed metrics recorder
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    MetricsRecorder openflagsMetricsRecorder(MeterRegistry registry,
            OpenFlagsProperties props) {
        return new MicrometerMetricsRecorder(registry, props.getMetrics().isTagFlagKey());
    }

    /**
     * Exposes the static tags configured under {@code openflags.metrics.tags.*} as
     * a
     * {@link MeterFilter} bean. Spring Boot Actuator auto-applies every
     * {@code MeterFilter}
     * bean to all auto-configured registries, so the tags become common to every
     * metric.
     * <p>
     * If no tags are configured the bean is still created but acts as a no-op
     * filter.
     * </p>
     *
     * @param props the openflags properties; used to read {@code metrics.tags}
     * @return a meter filter contributing the configured static tags
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    MeterFilter openflagsCommonTagsFilter(OpenFlagsProperties props) {
        Map<String, String> configured = props.getMetrics().getTags();
        if (configured == null || configured.isEmpty()) {
            return MeterFilter.commonTags(List.of());
        }
        List<Tag> tags = new ArrayList<>(configured.size());
        configured.forEach((k, v) -> tags.add(Tag.of(k, v)));
        return MeterFilter.commonTags(tags);
    }
}
