package com.openflags.provider.hybrid;

import com.openflags.core.evaluation.rule.Condition;
import com.openflags.core.evaluation.rule.MultiVariantRule;
import com.openflags.core.evaluation.rule.Operator;
import com.openflags.core.evaluation.rule.SplitRule;
import com.openflags.core.evaluation.rule.TargetingRule;
import com.openflags.core.evaluation.rule.WeightedVariant;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import com.openflags.core.parser.FlagFileParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotWriterTest {

    private final FlagFileParser parser = new FlagFileParser();

    @Test
    void roundTrip_yaml(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("snap.yaml");
        Map<String, Flag> flags = sampleFlags();

        new SnapshotWriter(SnapshotFormat.YAML).write(flags, target);

        Map<String, Flag> parsed = parser.parse(target);
        assertFlagsEqual(flags, parsed);
    }

    @Test
    void roundTrip_json(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("snap.json");
        Map<String, Flag> flags = sampleFlags();

        new SnapshotWriter(SnapshotFormat.JSON).write(flags, target);

        Map<String, Flag> parsed = parser.parse(target);
        assertFlagsEqual(flags, parsed);
    }

    @Test
    void roundTrip_multivariant(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("snap.json");
        Map<String, Flag> flags = Map.of(
                "ab-test", new Flag(
                        "ab-test", FlagType.STRING, FlagValue.of("control", FlagType.STRING),
                        true, Collections.emptyMap(),
                        List.of(new MultiVariantRule("ab", List.of(
                                new WeightedVariant(FlagValue.of("control", FlagType.STRING), 50),
                                new WeightedVariant(FlagValue.of("treatment", FlagType.STRING), 50)
                        )))));

        new SnapshotWriter(SnapshotFormat.JSON).write(flags, target);

        Map<String, Flag> parsed = parser.parse(target);
        assertThat(parsed).containsKey("ab-test");
        Flag f = parsed.get("ab-test");
        assertThat(f.rules()).hasSize(1);
        assertThat(f.rules().get(0)).isInstanceOf(MultiVariantRule.class);
        MultiVariantRule mvr = (MultiVariantRule) f.rules().get(0);
        assertThat(mvr.variants()).hasSize(2);
        assertThat(mvr.variants().get(0).value().asString()).isEqualTo("control");
        assertThat(mvr.variants().get(1).value().asString()).isEqualTo("treatment");
    }

    @Test
    void roundTrip_targetingRule(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("snap.json");
        Map<String, Flag> flags = Map.of(
                "dark-mode", new Flag(
                        "dark-mode", FlagType.BOOLEAN, FlagValue.of(false, FlagType.BOOLEAN),
                        true, Collections.emptyMap(),
                        List.of(new TargetingRule("ar-only",
                                List.of(new Condition("country", Operator.EQ, "AR")),
                                FlagValue.of(true, FlagType.BOOLEAN)))));

        new SnapshotWriter(SnapshotFormat.JSON).write(flags, target);

        Map<String, Flag> parsed = parser.parse(target);
        assertThat(parsed).containsKey("dark-mode");
        Flag f = parsed.get("dark-mode");
        assertThat(f.rules()).hasSize(1);
        assertThat(f.rules().get(0)).isInstanceOf(TargetingRule.class);
    }

    @Test
    void roundTrip_splitRule(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("snap.json");
        Map<String, Flag> flags = Map.of(
                "rollout", new Flag(
                        "rollout", FlagType.BOOLEAN, FlagValue.of(false, FlagType.BOOLEAN),
                        true, Collections.emptyMap(),
                        List.of(new SplitRule("twenty-percent", 20,
                                FlagValue.of(true, FlagType.BOOLEAN)))));

        new SnapshotWriter(SnapshotFormat.JSON).write(flags, target);

        Map<String, Flag> parsed = parser.parse(target);
        assertThat(parsed).containsKey("rollout");
        Flag f = parsed.get("rollout");
        assertThat(f.rules()).hasSize(1);
        SplitRule sr = (SplitRule) f.rules().get(0);
        assertThat(sr.percentage()).isEqualTo(20);
    }

    @Test
    void concurrentWrites_noParseErrors(@TempDir Path dir) throws InterruptedException {
        Path target = dir.resolve("snap.json");
        Map<String, Flag> flags = sampleFlags();
        SnapshotWriter writer = new SnapshotWriter(SnapshotFormat.JSON);
        FlagFileParser p = new FlagFileParser();

        int iterations = 100;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger parseErrors = new AtomicInteger(0);

        // writer thread
        Thread writerThread = Thread.ofVirtual().start(() -> {
            try {
                start.await();
                for (int i = 0; i < iterations; i++) {
                    writer.write(flags, target);
                }
            } catch (Exception e) {
                parseErrors.incrementAndGet();
            }
        });

        // reader thread — reads while writer is active
        Thread readerThread = Thread.ofVirtual().start(() -> {
            try {
                start.await();
                for (int i = 0; i < iterations; i++) {
                    try {
                        if (target.toFile().exists()) {
                            p.parse(target);
                        }
                    } catch (Exception e) {
                        parseErrors.incrementAndGet();
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        start.countDown();
        writerThread.join(10_000);
        readerThread.join(10_000);

        assertThat(parseErrors.get()).isZero();
    }

    // ---- helpers ----

    private Map<String, Flag> sampleFlags() {
        return Map.of(
                "flag-bool", new Flag("flag-bool", FlagType.BOOLEAN,
                        FlagValue.of(true, FlagType.BOOLEAN), true, Collections.emptyMap()),
                "flag-str", new Flag("flag-str", FlagType.STRING,
                        FlagValue.of("hello", FlagType.STRING), false, Collections.emptyMap()),
                "flag-num", new Flag("flag-num", FlagType.NUMBER,
                        FlagValue.of(42.0, FlagType.NUMBER), true, Collections.emptyMap())
        );
    }

    private void assertFlagsEqual(Map<String, Flag> expected, Map<String, Flag> actual) {
        assertThat(actual.keySet()).isEqualTo(expected.keySet());
        for (String key : expected.keySet()) {
            Flag e = expected.get(key);
            Flag a = actual.get(key);
            assertThat(a.key()).isEqualTo(e.key());
            assertThat(a.type()).isEqualTo(e.type());
            assertThat(a.enabled()).isEqualTo(e.enabled());
            assertThat(a.value()).isEqualTo(e.value());
        }
    }
}
