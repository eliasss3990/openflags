package com.openflags.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that the only compiled class in {@code openflags-core} that
 * references {@code io.micrometer.*} is
 * {@code MicrometerMetricsRecorder}. Acts as a regression guard for
 * ADR-501: any future change that leaks Micrometer types into another
 * class fails the build.
 *
 * <p>
 * The check inspects the raw bytecode of every {@code .class} file
 * under {@code target/classes/com/openflags/core/...} for the constant
 * pool string {@code io/micrometer}. No ASM dependency required: the
 * UTF-8 form of internal class names is searched directly.
 */
class NoMicrometerHardClassRefTest {

    private static final byte[] NEEDLE = "io/micrometer".getBytes();
    private static final String ALLOWED = "com/openflags/core/metrics/MicrometerMetricsRecorder";

    @Test
    void onlyMicrometerMetricsRecorderReferencesMicrometer() throws Exception {
        Path classesRoot = locateClassesRoot();
        List<Path> offenders = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(classesRoot)) {
            stream.filter(p -> p.toString().endsWith(".class"))
                    .forEach(p -> {
                        try {
                            String rel = relativeName(classesRoot, p);
                            boolean isAllowed = rel.equals(ALLOWED + ".class")
                                    || rel.startsWith(ALLOWED + "$");
                            if (containsNeedle(Files.readAllBytes(p)) && !isAllowed) {
                                offenders.add(p);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        assertThat(offenders)
                .as("classes outside %s must not reference io.micrometer", ALLOWED)
                .isEmpty();
    }

    private static String relativeName(Path root, Path classFile) {
        return root.relativize(classFile).toString().replace('\\', '/');
    }

    private static boolean containsNeedle(byte[] bytes) {
        outer: for (int i = 0; i <= bytes.length - NEEDLE.length; i++) {
            for (int j = 0; j < NEEDLE.length; j++) {
                if (bytes[i + j] != NEEDLE[j])
                    continue outer;
            }
            return true;
        }
        return false;
    }

    private static Path locateClassesRoot() throws Exception {
        URL url = OpenFlagsClient.class.getProtectionDomain()
                .getCodeSource().getLocation();
        Path p = Paths.get(url.toURI());
        // When tests run from Maven, code source is target/classes; from IDE may
        // be the same. We climb to find a directory that holds com/openflags/core.
        Path candidate = p.resolve("com").resolve("openflags").resolve("core");
        if (Files.isDirectory(candidate)) {
            return p;
        }
        throw new IllegalStateException(
                "could not locate classes root from " + p);
    }
}
