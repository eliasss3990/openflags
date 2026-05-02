package com.openflags.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drift guard for {@code spring-configuration-metadata.json}.
 *
 * <p>
 * Reflects over {@link OpenFlagsProperties} and its nested classes to enumerate
 * every Spring-bindable property, then asserts that each one has an entry in
 * the merged metadata (processor-generated +
 * {@code additional-spring-configuration-metadata.json}). Fails loudly when a
 * field is added without a corresponding metadata entry.
 */
class ConfigurationMetadataDriftTest {

    private static final String GENERATED = "META-INF/spring-configuration-metadata.json";
    private static final String ADDITIONAL = "META-INF/additional-spring-configuration-metadata.json";

    @Test
    void everyPropertyFieldHasMetadataEntry() throws Exception {
        Set<String> declared = new TreeSet<>(declaredPropertyNames());
        Set<String> documented = new TreeSet<>(documentedPropertyNames());

        Set<String> missing = new TreeSet<>(declared);
        missing.removeAll(documented);

        assertThat(missing)
                .as("Properties declared in OpenFlagsProperties without metadata entry; "
                        + "add a Javadoc on the field (auto) or an override in "
                        + ADDITIONAL)
                .isEmpty();
    }

    @Test
    void noStaleMetadataEntries() throws Exception {
        Set<String> declared = new TreeSet<>(declaredPropertyNames());
        Set<String> documented = new TreeSet<>(documentedPropertyNames());

        Set<String> stale = new TreeSet<>(documented);
        stale.removeAll(declared);

        assertThat(stale)
                .as("Metadata entries that no longer match a declared property; "
                        + "remove from " + ADDITIONAL + " or restore the field")
                .isEmpty();
    }

    private static List<String> declaredPropertyNames() throws IntrospectionException {
        List<String> names = new ArrayList<>();
        collect("openflags", OpenFlagsProperties.class, names);
        return names;
    }

    private static void collect(String prefix, Class<?> type, List<String> out) throws IntrospectionException {
        for (PropertyDescriptor pd : Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors()) {
            if (pd.getReadMethod() == null) {
                continue;
            }
            Class<?> ptype = pd.getPropertyType();
            String kebab = camelToKebab(pd.getName());
            String fullName = prefix + "." + kebab;
            if (isNestedHolder(ptype)) {
                collect(fullName, ptype, out);
            } else if (pd.getWriteMethod() != null || isMutableContainer(ptype)) {
                out.add(fullName);
            }
        }
    }

    private static boolean isNestedHolder(Class<?> type) {
        return type.getName().startsWith("com.openflags.spring.OpenFlagsProperties$");
    }

    private static boolean isMutableContainer(Class<?> type) {
        return Map.class.isAssignableFrom(type);
    }

    /**
     * Converts a Java bean property name (camelCase) to Spring relaxed binding
     * kebab-case. Assumes single-letter transitions ({@code baseUrl} →
     * {@code base-url}); does not split runs of upper-case (would convert
     * {@code baseURI} to {@code base-u-r-i}). All openflags property names use
     * camelCase with no acronyms in trailing position so this assumption holds.
     */
    private static String camelToKebab(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('-');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static List<String> documentedPropertyNames() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, JsonNode> merged = new LinkedHashMap<>();
        // Drift detection only cares about the property NAMES present, so the
        // merge order is irrelevant here (set union via putIfAbsent on both).
        // ConfigurationMetadataQualityTest uses the inverse order to make
        // additional-file overrides win when checking description contents.
        addProperties(mapper, GENERATED, merged);
        addProperties(mapper, ADDITIONAL, merged);
        return new ArrayList<>(merged.keySet());
    }

    private static void addProperties(ObjectMapper mapper, String resource, Map<String, JsonNode> out)
            throws IOException {
        try (InputStream in = ConfigurationMetadataDriftTest.class.getClassLoader()
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
