package com.openflags.core.metrics;

import com.openflags.core.evaluation.EvaluationEvent;
import com.openflags.core.evaluation.EvaluationReason;
import com.openflags.core.event.ChangeType;
import com.openflags.core.provider.ProviderState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Micrometer adapter for {@link MetricsRecorder}.
 *
 * <p>
 * This class is the ONLY place in {@code openflags-core} that depends
 * on {@code io.micrometer.*}. The boundary is asserted by
 * {@code NoMicrometerHardClassRefTest} (T6): nothing in
 * {@code openflags-core}'s public API surface (notably
 * {@link com.openflags.core.OpenFlagsClient} and
 * {@link com.openflags.core.OpenFlagsClientBuilder}) references this class
 * statically; it is loaded only when something actually constructs it. As a
 * result, consumers of {@code openflags-core} without Micrometer on their
 * classpath never trigger class-loading of this type.
 *
 * <p>
 * Made public so the Spring Boot starter (a separate module that already has
 * Micrometer on its classpath) can construct it directly to expose a
 * {@link MetricsRecorder} bean for wiring into provider beans. Application
 * code should not depend on this type; it is not part of the stable public
 * API of {@code openflags-core}.
 */
public final class MicrometerMetricsRecorder implements MetricsRecorder {

    private static final Logger log = LoggerFactory.getLogger(MicrometerMetricsRecorder.class);

    private static final int MAX_VARIANT_LEN = 64;
    private static final int VARIANT_PREFIX_LEN = 56;
    private static final int NORMALIZED_VARIANT_CACHE_CAP = 1_000;

    private final MeterRegistry registry;
    private final boolean tagFlagKey;

    private final Map<MeterKey, Counter> counters = new ConcurrentHashMap<>();
    private final Map<MeterKey, Timer> timers = new ConcurrentHashMap<>();
    private final Set<String> normalizedVariantsLogged = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicInteger normalizedVariantsLoggedSize = new AtomicInteger();
    private final AtomicLong droppedNormalizedVariants = new AtomicLong();

    public MicrometerMetricsRecorder(MeterRegistry registry, boolean tagFlagKey) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.tagFlagKey = tagFlagKey;
    }

    @Override
    public void recordEvaluation(EvaluationEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        Tags base = Tags.of(
                OpenFlagsMetrics.Tags.PROVIDER_TYPE, event.providerType(),
                OpenFlagsMetrics.Tags.TYPE, typeTag(event.type()),
                OpenFlagsMetrics.Tags.REASON, event.reason().name());
        if (tagFlagKey) {
            base = base.and(OpenFlagsMetrics.Tags.FLAG, event.flagKey());
            if (event.variant() != null) {
                base = base.and(OpenFlagsMetrics.Tags.VARIANT, normalizeVariant(event.variant()));
            }
        }
        counter(OpenFlagsMetrics.Names.EVALUATIONS_TOTAL, base).increment();
        timer(OpenFlagsMetrics.Names.EVALUATION_DURATION, base)
                .record(Duration.ofNanos(event.durationNanos()));

        String errorType = errorType(event.reason());
        if (errorType != null) {
            Tags errTags = Tags.of(
                    OpenFlagsMetrics.Tags.PROVIDER_TYPE, event.providerType(),
                    OpenFlagsMetrics.Tags.ERROR_TYPE, errorType);
            if (tagFlagKey)
                errTags = errTags.and(OpenFlagsMetrics.Tags.FLAG, event.flagKey());
            counter(OpenFlagsMetrics.Names.EVALUATIONS_ERRORS_TOTAL, errTags).increment();
        }
    }

    @Override
    public void recordPoll(String outcome, long durationNanos) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        Tags tags = Tags.of(OpenFlagsMetrics.Tags.OUTCOME, outcome);
        counter(OpenFlagsMetrics.Names.POLL_TOTAL, tags).increment();
        timer(OpenFlagsMetrics.Names.POLL_DURATION, tags).record(Duration.ofNanos(durationNanos));
    }

    @Override
    public void recordSnapshotWrite(String outcome, long durationNanos) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        Tags tags = Tags.of(OpenFlagsMetrics.Tags.OUTCOME, outcome);
        counter(OpenFlagsMetrics.Names.SNAPSHOT_WRITES_TOTAL, tags).increment();
        timer(OpenFlagsMetrics.Names.SNAPSHOT_WRITE_DURATION, tags).record(Duration.ofNanos(durationNanos));
    }

    @Override
    public void recordFlagChange(ChangeType type) {
        Objects.requireNonNull(type, "type must not be null");
        counter(OpenFlagsMetrics.Names.FLAG_CHANGES_TOTAL,
                Tags.of(OpenFlagsMetrics.Tags.CHANGE_TYPE, type.name())).increment();
    }

    @Override
    public void recordHybridFallback(String from, String to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        counter(OpenFlagsMetrics.Names.HYBRID_FALLBACK_TOTAL,
                Tags.of(OpenFlagsMetrics.Tags.FROM, from, OpenFlagsMetrics.Tags.TO, to)).increment();
    }

    @Override
    public void recordUnexpectedProviderError(String flagKey) {
        Objects.requireNonNull(flagKey, "flagKey must not be null");
        Tags tags = tagFlagKey
                ? Tags.of(OpenFlagsMetrics.Tags.FLAG, flagKey)
                : Tags.empty();
        counter(OpenFlagsMetrics.Names.EVALUATIONS_UNEXPECTED_ERRORS_TOTAL, tags).increment();
    }

    @Override
    public void recordListenerError(String listenerSimpleName) {
        Objects.requireNonNull(listenerSimpleName, "listenerSimpleName must not be null");
        counter(OpenFlagsMetrics.Names.EVALUATIONS_LISTENER_ERRORS_TOTAL,
                Tags.of(OpenFlagsMetrics.Tags.LISTENER, listenerSimpleName)).increment();
    }

    @Override
    public void recordHybridPollSuccess(long durationNanos) {
        counter(OpenFlagsMetrics.Names.POLL_SUCCESS, Tags.empty()).increment();
        timer(OpenFlagsMetrics.Names.POLL_LATENCY,
                Tags.of(OpenFlagsMetrics.Tags.OUTCOME, "success"))
                .record(Duration.ofNanos(durationNanos));
    }

    @Override
    public void recordHybridPollFailure(long durationNanos) {
        counter(OpenFlagsMetrics.Names.POLL_FAILURE, Tags.empty()).increment();
        timer(OpenFlagsMetrics.Names.POLL_LATENCY,
                Tags.of(OpenFlagsMetrics.Tags.OUTCOME, "failure"))
                .record(Duration.ofNanos(durationNanos));
    }

    @Override
    public void recordHybridFallbackActivation(String cause) {
        Objects.requireNonNull(cause, "cause must not be null");
        counter(OpenFlagsMetrics.Names.HYBRID_FALLBACK_ACTIVATIONS,
                Tags.of(OpenFlagsMetrics.Tags.CAUSE, cause)).increment();
    }

    @Override
    public void recordHybridFallbackDeactivation(long fallbackDurationNanos) {
        counter(OpenFlagsMetrics.Names.HYBRID_FALLBACK_DEACTIVATIONS, Tags.empty()).increment();
        timer(OpenFlagsMetrics.Names.HYBRID_FALLBACK_DURATION, Tags.empty())
                .record(Duration.ofNanos(fallbackDurationNanos));
    }

    @Override
    public void recordHybridEvaluationLatency(String source, long durationNanos) {
        Objects.requireNonNull(source, "source must not be null");
        timer(OpenFlagsMetrics.Names.HYBRID_EVALUATION_LATENCY,
                Tags.of(OpenFlagsMetrics.Tags.SOURCE, source))
                .record(Duration.ofNanos(durationNanos));
    }

    @Override
    public void recordHybridStateTransition(String from, String to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        counter(OpenFlagsMetrics.Names.HYBRID_STATE_TRANSITIONS,
                Tags.of(OpenFlagsMetrics.Tags.FROM, from, OpenFlagsMetrics.Tags.TO, to)).increment();
    }

    @Override
    public void registerGauge(String name, Iterable<Tag> tags, Supplier<Number> supplier) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(tags, "tags must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        Tags micrometerTags = toMicrometerTags(tags);
        io.micrometer.core.instrument.Gauge.builder(name, supplier, s -> s.get().doubleValue())
                .tags(micrometerTags)
                .strongReference(true)
                .register(registry);
    }

    /**
     * Stable, non-ordinal mapping of {@link ProviderState} to a numeric
     * code suitable for a gauge. Future enum additions MUST be assigned
     * codes >= 6 without reordering existing entries (ADR-501).
     *
     * @param state state to map; {@code null} maps to {@code -1}
     * @return numeric code, or {@code -1} for unknown values
     */
    @SuppressWarnings("deprecation") // STALE branch retained until 2.0 removal (ADR-6 / PR-12b)
    static int providerStateCode(ProviderState state) {
        if (state == null)
            return -1;
        return switch (state) {
            case NOT_READY -> 0;
            case READY -> 1;
            case DEGRADED -> 2;
            case ERROR -> 3;
            case STALE -> 4;
            case SHUTDOWN -> 5;
        };
    }

    private Counter counter(String name, Tags tags) {
        return counters.computeIfAbsent(new MeterKey(name, tags),
                k -> Counter.builder(k.name).tags(k.tags).register(registry));
    }

    private Timer timer(String name, Tags tags) {
        return timers.computeIfAbsent(new MeterKey(name, tags),
                k -> Timer.builder(k.name).tags(k.tags).register(registry));
    }

    private static Tags toMicrometerTags(Iterable<Tag> tags) {
        Tags out = Tags.empty();
        for (Tag t : tags) {
            out = out.and(t.key(), t.value());
        }
        return out;
    }

    static String typeTag(Class<?> type) {
        if (type == Boolean.class)
            return "boolean";
        if (type == String.class)
            return "string";
        if (Number.class.isAssignableFrom(type))
            return "number";
        return "object";
    }

    static String errorType(EvaluationReason reason) {
        return switch (reason) {
            case FLAG_NOT_FOUND -> "MISSING";
            case TYPE_MISMATCH -> "TYPE_MISMATCH";
            case PROVIDER_ERROR -> "PROVIDER_ERROR";
            default -> null;
        };
    }

    /**
     * Normalizes a variant tag value to bound cardinality without silent
     * collisions. Values <= {@value #MAX_VARIANT_LEN} chars pass through;
     * longer values become {@code <prefix(56)>~<7-char-hex-hash>}.
     * Logs a single WARN per distinct normalized value, with a hard cap
     * to prevent unbounded log growth.
     */
    String normalizeVariant(String variant) {
        if (variant.length() <= MAX_VARIANT_LEN)
            return variant;
        String prefix = variant.substring(0, VARIANT_PREFIX_LEN);
        // Mask to 28 bits so the formatted hex is exactly 7 chars; %07x only
        // pads short values, it does not truncate longer ones, so a wider
        // mask would yield 8-char output and break the fixed-length contract.
        String hash = String.format("%07x",
                Integer.toUnsignedLong(variant.hashCode()) & 0x0FFFFFFFL);
        String normalized = prefix + "~" + hash;
        // CAS guard on cache size so concurrent threads cannot race past
        // the cap; a slot is reserved before calling add() and released
        // via decrementAndGet() if the element was already present.
        int reserved = normalizedVariantsLoggedSize.get();
        while (reserved < NORMALIZED_VARIANT_CACHE_CAP) {
            if (normalizedVariantsLoggedSize.compareAndSet(reserved, reserved + 1)) {
                if (normalizedVariantsLogged.add(normalized)) {
                    log.warn("openflags: variant tag normalized due to length>{}: {} -> {}",
                            MAX_VARIANT_LEN,
                            variant.substring(0, Math.min(80, variant.length())),
                            normalized);
                } else {
                    // duplicate: release the reservation
                    normalizedVariantsLoggedSize.decrementAndGet();
                }
                return normalized;
            }
            reserved = normalizedVariantsLoggedSize.get();
        }
        long dropped = droppedNormalizedVariants.incrementAndGet();
        if (Long.bitCount(dropped) == 1) {
            log.warn("openflags: normalized variant log cache full, dropped {} additional WARNs", dropped);
        }
        return normalized;
    }

    /** Composite key for the meter caches. */
    private record MeterKey(String name, Tags tags) {
        MeterKey {
            Objects.requireNonNull(name);
            Objects.requireNonNull(tags);
        }
    }

}
