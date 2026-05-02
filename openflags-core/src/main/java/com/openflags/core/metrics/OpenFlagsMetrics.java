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
 * @apiNote Use these constants instead of string literals when configuring
 *          dashboards programmatically (e.g. via Grafana provisioning) or
 *          when filtering metrics in tests.
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
    }
}
