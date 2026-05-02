package com.openflags.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.openflags.core.parser.FlagFileParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Doctest-style guard: every YAML snippet in {@code README.md} that defines a
 * {@code flags:} block must parse cleanly through {@link FlagFileParser}.
 * Catches drift between docs and code (e.g. {@code type:} vs {@code kind:} on
 * rules) before it reaches readers.
 */
class ReadmeSnippetsParseTest {

    @Test
    void allReadmeFlagSnippets_parseSuccessfully() throws Exception {
        Path readme = locateReadme();
        String content = Files.readString(readme);

        List<String> snippets = extractYamlFlagSnippets(content);
        assertThat(snippets)
                .as("expected at least one ```yaml flags: snippet in README")
                .isNotEmpty();

        FlagFileParser parser = new FlagFileParser();
        JsonMapper yaml = JsonMapper.builder(new YAMLFactory()).build();

        for (int i = 0; i < snippets.size(); i++) {
            String snippet = snippets.get(i);
            JsonNode root = yaml.readTree(snippet);
            try {
                parser.parseFlags(root, "readme-snippet-" + i);
            } catch (RuntimeException e) {
                throw new AssertionError(
                        "README YAML snippet #" + i + " failed to parse:\n" + snippet, e);
            }
        }
    }

    static Path locateReadme() {
        String root = System.getProperty("maven.multiModuleProjectDirectory");
        if (root != null) {
            Path candidate = Paths.get(root, "README.md");
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        for (Path p : new Path[] { Paths.get("README.md"), Paths.get("../README.md"),
                Paths.get("../../README.md") }) {
            if (Files.exists(p)) {
                return p.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("README.md not found (cwd=" + Paths.get("").toAbsolutePath()
                + ", maven.multiModuleProjectDirectory=" + root + ")");
    }

    /** True if the snippet has {@code flags:} as a top-level key (column 0). */
    private static boolean hasTopLevelFlagsKey(String body) {
        for (String line : body.split("\\R", -1)) {
            if (line.startsWith("flags:")) {
                return true;
            }
        }
        return false;
    }

    /** Skip pseudo-YAML schema blocks that use placeholders like {@code <flag-key>}. */
    private static boolean isSchemaPlaceholder(String body) {
        return body.contains("<flag-key>")
                || body.contains("<value matching")
                || body.contains("boolean | string");
    }

    /**
     * Extracts {@code ```yaml ...```} fenced blocks whose body has {@code flags:}
     * as a top-level key. Configuration fragments (e.g. {@code application.yml})
     * and pseudo-YAML schema blocks are skipped — those don't go through
     * {@link FlagFileParser}.
     */
    private static List<String> extractYamlFlagSnippets(String md) {
        List<String> out = new ArrayList<>();
        String[] lines = md.split("\\R", -1);
        boolean inBlock = false;
        StringBuilder buf = new StringBuilder();
        for (String line : lines) {
            if (!inBlock) {
                if (line.trim().equals("```yaml")) {
                    inBlock = true;
                    buf.setLength(0);
                }
            } else {
                if (line.trim().equals("```")) {
                    String body = buf.toString();
                    if (hasTopLevelFlagsKey(body) && !isSchemaPlaceholder(body)) {
                        out.add(body);
                    }
                    inBlock = false;
                } else {
                    buf.append(line).append('\n');
                }
            }
        }
        return out;
    }
}
