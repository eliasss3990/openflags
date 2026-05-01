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
 * Micrometer adapter for {@link MetricsRecorder}. Package-private: it MUST
 * be referenced only via reflection by
 * {@link com.openflags.core.OpenFlagsClientBuilder} so that consumers
 * without Micrometer on their classpath never load this class.
 *
 * <p>
 * This class is the ONLY place in {@code openflags-core} that depends
 * on {@code io.micrometer.*}. The boundary is asserted by
 * {@code NoMicrometerHardClassRefTest} (T6).
 */
final class MicrometerMetricsRecorder implements MetricsRecorder {

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

    MicrometerMetricsRecorder(MeterRegistry registry, boolean tagFlagKey) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.tagFlagKey = tagFlagKey;
    }

    @Override
    public void recordEvaluation(EvaluationEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        Tags base = Tags.of(
                "provider.type", event.providerType(),
                "type", typeTag(event.type()),
                "reason", event.reason().name());
        if (tagFlagKey) {
            base = base.and("flag", event.flagKey());
            if (event.variant() != null) {
                base = base.and("variant", normalizeVariant(event.variant()));
            }
        }
        counter("openflags.evaluations.total", base).increment();
        timer("openflags.evaluation.duration", base)
                .record(Duration.ofNanos(event.durationNanos()));

        String errorType = errorType(event.reason());
        if (errorType != null) {
            Tags errTags = Tags.of("provider.type", event.providerType(), "error.type", errorType);
            if (tagFlagKey)
                errTags = errTags.and("flag", event.flagKey());
            counter("openflags.evaluations.errors.total", errTags).increment();
        }
    }

    @Override
    public void recordPoll(String outcome, long durationNanos) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        Tags tags = Tags.of("outcome", outcome);
        counter("openflags.poll.total", tags).increment();
        timer("openflags.poll.duration", tags).record(Duration.ofNanos(durationNanos));
    }

    @Override
    public void recordSnapshotWrite(String outcome, long durationNanos) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        Tags tags = Tags.of("outcome", outcome);
        counter("openflags.snapshot.writes.total", tags).increment();
        timer("openflags.snapshot.write.duration", tags).record(Duration.ofNanos(durationNanos));
    }

    @Override
    public void recordFlagChange(ChangeType type) {
        Objects.requireNonNull(type, "type must not be null");
        counter("openflags.flag_changes.total", Tags.of("change_type", type.name())).increment();
    }

    @Override
    public void recordHybridFallback(String from, String to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        counter("openflags.hybrid.fallback.total", Tags.of("from", from, "to", to)).increment();
    }

    @Override
    public void recordListenerError(String listenerSimpleName) {
        Objects.requireNonNull(listenerSimpleName, "listenerSimpleName must not be null");
        counter("openflags.evaluations.listener.errors.total",
                Tags.of("listener", listenerSimpleName)).increment();
    }

    @Override
    public void registerGauge(String name, Iterable<Tag> tags, Supplier<Number> supplier) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(tags, "tags must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        Tags micrometerTags = toMicrometerTags(tags);
        registry.gauge(name, micrometerTags, supplier, s -> s.get().doubleValue());
    }

    /**
     * Stable, non-ordinal mapping of {@link ProviderState} to a numeric
     * code suitable for a gauge. Future enum additions MUST be assigned
     * codes >= 6 without reordering existing entries (ADR-501).
     *
     * @param state state to map; {@code null} maps to {@code -1}
     * @return numeric code, or {@code -1} for unknown values
     */
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
            case PROVIDER_ERROR -> "RULE_ERROR";
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
