package com.openflags.core.provider;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderDiagnosticsContractTest {

    @Test
    void fakeImplExposesAllFields() {
        Instant now = Instant.parse("2026-05-01T00:00:00Z");
        FakeDiagnostics diag = new FakeDiagnostics("file", now, 7,
                Map.of("file.path", "/tmp/flags.yml", "file.format", "yaml"));

        assertThat(diag.providerType()).isEqualTo("file");
        assertThat(diag.lastUpdate()).isEqualTo(now);
        assertThat(diag.flagCount()).isEqualTo(7);
        assertThat(diag.diagnostics())
                .containsEntry("file.path", "/tmp/flags.yml")
                .containsEntry("file.format", "yaml");
    }

    @Test
    void lastUpdateIsNullableAndFlagCountZeroBeforeInit() {
        FakeDiagnostics diag = new FakeDiagnostics("memory", null, 0, Map.of());

        assertThat(diag.lastUpdate()).isNull();
        assertThat(diag.flagCount()).isZero();
        assertThat(diag.diagnostics()).isEmpty();
    }

    @Test
    void diagnosticsAreReadOnlyForCallers() {
        Map<String, Object> backing = new LinkedHashMap<>();
        backing.put("k", "v");
        FakeDiagnostics diag = new FakeDiagnostics("file", Instant.now(), 1, backing);

        Map<String, Object> exposed = diag.diagnostics();
        assertThat(exposed).containsEntry("k", "v");
        assertThat(exposed).isUnmodifiable();
    }

    @Test
    void diagnosticsCallsAreCheap() {
        AtomicInteger calls = new AtomicInteger();
        ProviderDiagnostics diag = new ProviderDiagnostics() {
            @Override
            public String providerType() {
                return "stub";
            }

            @Override
            public Map<String, Object> diagnostics() {
                calls.incrementAndGet();
                return Map.of();
            }

            @Override
            public Instant lastUpdate() {
                return null;
            }

            @Override
            public int flagCount() {
                return 0;
            }
        };

        for (int i = 0; i < 1000; i++)
            diag.diagnostics();
        assertThat(calls).hasValue(1000);
    }

    private static final class FakeDiagnostics implements ProviderDiagnostics {
        private final String type;
        private final Instant lastUpdate;
        private final int count;
        private final Map<String, Object> data;

        FakeDiagnostics(String type, Instant lastUpdate, int count,
                Map<String, Object> data) {
            this.type = type;
            this.lastUpdate = lastUpdate;
            this.count = count;
            this.data = Map.copyOf(data);
        }

        @Override
        public String providerType() {
            return type;
        }

        @Override
        public Map<String, Object> diagnostics() {
            return data;
        }

        @Override
        public Instant lastUpdate() {
            return lastUpdate;
        }

        @Override
        public int flagCount() {
            return count;
        }
    }
}
