package com.openflags.core.metrics;

import java.util.Objects;

/**
 * Immutable key/value pair attached to a metric.
 *
 * <p>
 * This is OpenFlags' own tag type. The public API never exposes
 * Micrometer types so consumers can use {@link MetricsRecorder} without
 * pulling Micrometer onto their classpath. Internal adapters translate
 * to {@code io.micrometer.core.instrument.Tag} when present.
 *
 * @param key   tag name, never {@code null}
 * @param value tag value, never {@code null}
 * @since 0.5.0
 */
public record Tag(String key, String value) {

    public Tag {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}
