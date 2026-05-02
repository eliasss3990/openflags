package com.openflags.spring;

import com.openflags.core.evaluation.EvaluationReason;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drift guard: the README's {@code EvaluationReason} table must list every
 * value of the {@link EvaluationReason} enum, no more and no less.
 */
class EvaluationReasonReadmeTableTest {

    private static final Pattern ROW = Pattern.compile("^\\|\\s*`([A-Z_]+)`\\s*\\|.*\\|\\s*$");

    @Test
    void readmeTableMatchesEnumValues() throws Exception {
        Path readme = locateReadme();
        String content = Files.readString(readme);

        Set<String> documented = extractRowsAfterHeading(content, "### EvaluationReason");
        Set<String> actual = new LinkedHashSet<>();
        Arrays.stream(EvaluationReason.values()).map(Enum::name).forEach(actual::add);

        assertThat(documented)
                .as("README EvaluationReason table must match enum exactly")
                .containsExactlyInAnyOrderElementsOf(actual);
    }

    private static Set<String> extractRowsAfterHeading(String md, String heading) {
        Set<String> out = new LinkedHashSet<>();
        String[] lines = md.split("\\R", -1);
        boolean afterHeading = false;
        for (String line : lines) {
            if (!afterHeading) {
                if (line.startsWith(heading)) {
                    afterHeading = true;
                }
                continue;
            }
            if (line.startsWith("### ") || line.startsWith("## ")) {
                break;
            }
            Matcher m = ROW.matcher(line);
            if (m.matches()) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    private static Path locateReadme() {
        return ReadmeSnippetsParseTest.locateReadme();
    }
}
