/**
 * Metrics SPI for OpenFlags. Defines the
 * {@link com.openflags.core.metrics.MetricsRecorder}
 * sink and its companion {@link com.openflags.core.metrics.Tag} record.
 *
 * <p>
 * This package is intentionally free of any third-party metrics
 * library imports. Concrete adapters (e.g. for Micrometer) live in
 * package-private classes and are wired by the Spring Boot starter when
 * the corresponding library is on the classpath.
 *
 * @since 0.5.0
 */
package com.openflags.core.metrics;
