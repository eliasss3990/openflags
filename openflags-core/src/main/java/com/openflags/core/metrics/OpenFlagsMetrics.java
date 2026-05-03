package com.openflags.core.metrics;

/**
 * Public catalog of meter names and tag keys emitted by openflags through
 * Micrometer.
 *
 * <p>
 * <strong>API stability:</strong> the names and tag keys exposed here are part
 * of the public contract since 1.0. Renaming or removing any of them is a
 * <em>major</em> version bump because dashboards, alerts and SLO queries that
 * reference them would otherwise break silently. New names and tags MAY be
 * added in minor releases.
 *
 * <p>
 * <strong>API note:</strong> use these constants instead of string literals
 * when configuring dashboards programmatically (e.g. via Grafana provisioning)
 * or when filtering metrics in tests.
 */
public final class OpenFlagsMetrics {

    private OpenFlagsMetrics() {
    }

    /** Canonical meter names. */
    public static final class Names {

        private Names() {
        }

        /** Counter: total flag evaluations. */
        public static final String EVALUATIONS_TOTAL = "openflags.evaluations.total";

        /** Timer: per-evaluation latency. */
        public static final String EVALUATION_DURATION = "openflags.evaluation.duration";

        /**
         * Counter: evaluations that produced an error reason
         * (missing/type-mismatch/etc).
         */
        public static final String EVALUATIONS_ERRORS_TOTAL = "openflags.evaluations.errors.total";

        /** Counter: total remote provider polls (success + failure). */
        public static final String POLL_TOTAL = "openflags.poll.total";

        /** Timer: per-poll latency. */
        public static final String POLL_DURATION = "openflags.poll.duration";

        /** Counter: total snapshot writes performed by the hybrid provider. */
        public static final String SNAPSHOT_WRITES_TOTAL = "openflags.snapshot.writes.total";

        /** Timer: per-snapshot-write latency. */
        public static final String SNAPSHOT_WRITE_DURATION = "openflags.snapshot.write.duration";

        /** Counter: detected flag changes (added/removed/value changed). */
        public static final String FLAG_CHANGES_TOTAL = "openflags.flag_changes.total";

        /** Counter: hybrid fallback transitions between remote and file. */
        public static final String HYBRID_FALLBACK_TOTAL = "openflags.hybrid.fallback.total";

        /** Counter: exceptions thrown by user-provided {@code EvaluationListener}s. */
        public static final String EVALUATIONS_LISTENER_ERRORS_TOTAL = "openflags.evaluations.listener.errors.total";

        /**
         * Counter: unexpected (non-{@code ProviderException}) exceptions thrown by a
         * {@code FlagProvider} while resolving a flag. Indicates a programming error
         * that bypassed the documented provider contract.
         */
        public static final String EVALUATIONS_UNEXPECTED_ERRORS_TOTAL = "openflags.evaluations.unexpected.errors.total";

        // ---- Hybrid-specific metrics (added in 1.1) ----

        /** Counter: successful remote polls emitted by the hybrid provider. */
        public static final String POLL_SUCCESS = "openflags.poll.success";

        /** Counter: failed remote polls emitted by the hybrid provider. */
        public static final String POLL_FAILURE = "openflags.poll.failure";

        /** Timer: per-poll latency emitted by the hybrid provider. */
        public static final String POLL_LATENCY = "openflags.poll.latency";

        /**
         * Counter: number of times the hybrid provider activated fallback mode.
         * Tagged with {@link Tags#CAUSE}.
         */
        public static final String HYBRID_FALLBACK_ACTIVATIONS = "openflags.hybrid.fallback.activations";

        /** Counter: number of times the hybrid provider deactivated fallback mode. */
        public static final String HYBRID_FALLBACK_DEACTIVATIONS = "openflags.hybrid.fallback.deactivations";

        /**
         * Gauge (0/1): whether the hybrid provider is currently in fallback mode
         * (1 = active, 0 = not active).
         */
        public static final String HYBRID_FALLBACK_ACTIVE = "openflags.hybrid.fallback.active";

        /**
         * Timer: duration spent in fallback mode, recorded when fallback deactivates.
         */
        public static final String HYBRID_FALLBACK_DURATION = "openflags.hybrid.fallback.duration";

        /**
         * Timer: flag evaluation latency partitioned by source.
         * Tagged with {@link Tags#SOURCE}.
         */
        public static final String HYBRID_EVALUATION_LATENCY = "openflags.hybrid.evaluation.latency";

        /** Counter: cache hits on the hybrid provider's primary-side cache. */
        public static final String HYBRID_PRIMARY_CACHE_HITS = "openflags.hybrid.primary.cache.hits";

        /** Counter: cache misses on the hybrid provider's primary-side cache. */
        public static final String HYBRID_PRIMARY_CACHE_MISSES = "openflags.hybrid.primary.cache.misses";

        /**
         * Counter: state transitions of the hybrid provider.
         * Tagged with {@link Tags#FROM} and {@link Tags#TO}.
         */
        public static final String HYBRID_STATE_TRANSITIONS = "openflags.hybrid.state.transitions";

        /**
         * Gauge: current state of the hybrid provider encoded as a numeric code.
         * Uses the same encoding as {@code MicrometerMetricsRecorder#providerStateCode}.
         */
        public static final String HYBRID_STATE_CURRENT = "openflags.hybrid.state.current";
    }

    /** Canonical tag keys attached to the meters listed in {@link Names}. */
    public static final class Tags {

        private Tags() {
        }

        /** Tag: provider type ({@code file}, {@code remote}, {@code hybrid}, …). */
        public static final String PROVIDER_TYPE = "provider.type";

        /**
         * Tag: requested flag value type
         * ({@code boolean}/{@code string}/{@code number}/{@code object}).
         */
        public static final String TYPE = "type";

        /** Tag: {@code EvaluationReason} name. */
        public static final String REASON = "reason";

        /** Tag: flag key (only present when flag-key tagging is enabled). */
        public static final String FLAG = "flag";

        /** Tag: variant name from a multi-variant rule. */
        public static final String VARIANT = "variant";

        /** Tag: error category emitted on the errors counter. */
        public static final String ERROR_TYPE = "error.type";

        /** Tag: poll/snapshot outcome ({@code success}/{@code failure}/…). */
        public static final String OUTCOME = "outcome";

        /** Tag: change-event category (added/removed/value-changed). */
        public static final String CHANGE_TYPE = "change_type";

        /** Tag: provider being left during a hybrid fallback transition. */
        public static final String FROM = "from";

        /** Tag: provider being entered during a hybrid fallback transition. */
        public static final String TO = "to";

        /** Tag: {@code Class.getSimpleName()} of the failing listener. */
        public static final String LISTENER = "listener";

        /**
         * Tag: cause of a fallback activation.
         * Bounded set: {@code primary_error}, {@code primary_timeout},
         * {@code primary_state_error}, {@code primary_not_ready}.
         */
        public static final String CAUSE = "cause";

        /**
         * Tag: evaluation source in a hybrid provider.
         * Values: {@code primary}, {@code fallback}.
         */
        public static final String SOURCE = "source";
    }
}
