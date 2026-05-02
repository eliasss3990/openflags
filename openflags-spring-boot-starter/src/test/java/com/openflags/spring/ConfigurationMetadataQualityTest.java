package com.openflags.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quality guard for {@code spring-configuration-metadata.json}.
 *
 * <p>
 * Asserts that every documented property carries a non-empty
 * {@code description}
 * (so IDE tooltips never render blank) and that descriptions do not leak raw
 * Javadoc tags such as {@code {@code ...}} or {@code {@link ...}} that would
 * surface verbatim in the IDE.
 */
class ConfigurationMetadataQualityTest {

    private static final String GENERATED = "META-INF/spring-configuration-metadata.json";
    private static final String ADDITIONAL = "META-INF/additional-spring-configuration-metadata.json";

    @Test
    void everyEntryHasDescription() throws Exception {
        Map<String, JsonNode> entries = mergedEntries();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, JsonNode> e : entries.entrySet()) {
            String desc = e.getValue().path("description").asText("");
            if (desc.isBlank()) {
                missing.add(e.getKey());
            }
        }
        assertThat(missing)
                .as("Properties without description: enrich the field Javadoc or "
                        + "override in " + ADDITIONAL)
                .isEmpty();
    }

    @Test
    void descriptionsDoNotLeakJavadocTags() throws Exception {
        Map<String, JsonNode> entries = mergedEntries();
        List<String> leaking = new ArrayList<>();
        for (Map.Entry<String, JsonNode> e : entries.entrySet()) {
            String desc = e.getValue().path("description").asText("");
            if (desc.contains("{@code") || desc.contains("{@link")) {
                leaking.add(e.getKey());
            }
        }
        assertThat(leaking)
                .as("Descriptions render verbatim in IDE tooltips; replace Javadoc "
                        + "tags with plain text or override in " + ADDITIONAL)
                .isEmpty();
    }

    private static Map<String, JsonNode> mergedEntries() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, JsonNode> merged = new LinkedHashMap<>();
        // Process additional first so its overrides win for entries that the
        // processor also generates (cleaner descriptions, explicit defaults).
        addProperties(mapper, ADDITIONAL, merged);
        addProperties(mapper, GENERATED, merged);
        return merged;
    }

    private static void addProperties(ObjectMapper mapper, String resource, Map<String, JsonNode> out)
            throws IOException {
        try (InputStream in = ConfigurationMetadataQualityTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) {
                return;
            }
            JsonNode root = mapper.readTree(in);
            JsonNode props = root.path("properties");
            if (props.isArray()) {
                for (Iterator<JsonNode> it = props.elements(); it.hasNext();) {
                    JsonNode entry = it.next();
                    String name = entry.path("name").asText(null);
                    if (name != null && !name.isEmpty()) {
                        out.putIfAbsent(name, entry);
                    }
                }
            }
        }
    }
}
