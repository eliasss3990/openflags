package com.openflags.core.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the public string values of {@link OpenFlagsMetrics.Names} and
 * {@link OpenFlagsMetrics.Tags}. Renaming any of these is a major version
 * bump because dashboards/alerts referencing them would break silently.
 */
class MetricNamesContractTest {

    @Test
    void meterNames_haveStableValues() {
        assertThat(OpenFlagsMetrics.Names.EVALUATIONS_TOTAL).isEqualTo("openflags.evaluations.total");
        assertThat(OpenFlagsMetrics.Names.EVALUATION_DURATION).isEqualTo("openflags.evaluation.duration");
        assertThat(OpenFlagsMetrics.Names.EVALUATIONS_ERRORS_TOTAL).isEqualTo("openflags.evaluations.errors.total");
        assertThat(OpenFlagsMetrics.Names.POLL_TOTAL).isEqualTo("openflags.poll.total");
        assertThat(OpenFlagsMetrics.Names.POLL_DURATION).isEqualTo("openflags.poll.duration");
        assertThat(OpenFlagsMetrics.Names.SNAPSHOT_WRITES_TOTAL).isEqualTo("openflags.snapshot.writes.total");
        assertThat(OpenFlagsMetrics.Names.SNAPSHOT_WRITE_DURATION).isEqualTo("openflags.snapshot.write.duration");
        assertThat(OpenFlagsMetrics.Names.FLAG_CHANGES_TOTAL).isEqualTo("openflags.flag_changes.total");
        assertThat(OpenFlagsMetrics.Names.HYBRID_FALLBACK_TOTAL).isEqualTo("openflags.hybrid.fallback.total");
        assertThat(OpenFlagsMetrics.Names.EVALUATIONS_LISTENER_ERRORS_TOTAL)
                .isEqualTo("openflags.evaluations.listener.errors.total");
    }

    @Test
    void tagKeys_haveStableValues() {
        assertThat(OpenFlagsMetrics.Tags.PROVIDER_TYPE).isEqualTo("provider.type");
        assertThat(OpenFlagsMetrics.Tags.TYPE).isEqualTo("type");
        assertThat(OpenFlagsMetrics.Tags.REASON).isEqualTo("reason");
        assertThat(OpenFlagsMetrics.Tags.FLAG).isEqualTo("flag");
        assertThat(OpenFlagsMetrics.Tags.VARIANT).isEqualTo("variant");
        assertThat(OpenFlagsMetrics.Tags.ERROR_TYPE).isEqualTo("error.type");
        assertThat(OpenFlagsMetrics.Tags.OUTCOME).isEqualTo("outcome");
        assertThat(OpenFlagsMetrics.Tags.CHANGE_TYPE).isEqualTo("change_type");
        assertThat(OpenFlagsMetrics.Tags.FROM).isEqualTo("from");
        assertThat(OpenFlagsMetrics.Tags.TO).isEqualTo("to");
        assertThat(OpenFlagsMetrics.Tags.LISTENER).isEqualTo("listener");
    }
}
