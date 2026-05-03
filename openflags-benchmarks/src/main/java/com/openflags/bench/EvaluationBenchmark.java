package com.openflags.bench;

import com.openflags.core.OpenFlagsClient;
import com.openflags.core.evaluation.EvaluationContext;
import com.openflags.provider.file.FileFlagProvider;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Benchmark)
public class EvaluationBenchmark {

    private OpenFlagsClient client;
    private Path flagsFile;
    private EvaluationContext ctxWithUser;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        flagsFile = Files.createTempFile("flags-bench", ".json");
        Files.writeString(flagsFile, """
                {
                  "flags": {
                    "always-on": { "type": "boolean", "default": true },
                    "always-off": { "type": "boolean", "default": false },
                    "string-flag": { "type": "string", "default": "hello" }
                  }
                }
                """);
        FileFlagProvider provider = FileFlagProvider.builder().path(flagsFile).build();
        provider.init();
        client = OpenFlagsClient.builder().provider(provider).build();
        ctxWithUser = EvaluationContext.of("user-bench-001");
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (client != null) {
            client.shutdown();
        }
        Files.deleteIfExists(flagsFile);
    }

    @Benchmark
    public boolean booleanFlagOn() {
        return client.getBooleanResult("always-on", false, EvaluationContext.empty()).value();
    }

    @Benchmark
    public boolean booleanFlagOff() {
        return client.getBooleanResult("always-off", true, EvaluationContext.empty()).value();
    }

    @Benchmark
    public String stringFlag() {
        return client.getStringResult("string-flag", "default", EvaluationContext.empty()).value();
    }

    @Benchmark
    public boolean booleanFlagWithContext() {
        return client.getBooleanResult("always-on", false, ctxWithUser).value();
    }

    @Benchmark
    public boolean missingFlagFallback() {
        return client.getBooleanResult("does-not-exist", false, EvaluationContext.empty()).value();
    }
}
