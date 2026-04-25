package com.openflags.provider.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Parses feature flag definition files in YAML or JSON format.
 * <p>
 * The file format is detected strictly by extension:
 * {@code .yml} and {@code .yaml} are parsed as YAML; {@code .json} as JSON.
 * Any other extension causes a {@link ProviderException} with a descriptive message.
 * </p>
 *
 * <h3>Expected file structure (YAML example)</h3>
 * <pre>
 * flags:
 *   dark-mode:
 *     type: boolean
 *     value: true
 *     enabled: true          # optional, defaults to true
 *     description: "..."     # optional metadata
 *   rollout-rate:
 *     type: number
 *     value: 0.25
 * </pre>
 */
public final class FlagFileParser {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * Parses the flag file at the given path.
     *
     * @param path the path to the flag definition file
     * @return an unmodifiable map of flag key to {@link Flag}
     * @throws ProviderException if the file cannot be read, has an unrecognized extension,
     *                           or does not conform to the expected structure
     */
    public Map<String, Flag> parse(Path path) {
        ObjectMapper mapper = resolveMapper(path);
        JsonNode root;
        try {
            root = mapper.readTree(path.toFile());
        } catch (IOException e) {
            throw new ProviderException("Failed to read flag file: " + path, e);
        }

        if (root == null || root.isNull() || root.isMissingNode()) {
            throw new ProviderException("Flag file '" + path.getFileName() + "' is empty or invalid");
        }

        JsonNode flagsNode = root.path("flags");
        if (flagsNode.isMissingNode() || flagsNode.isNull()) {
            throw new ProviderException("Flag file '" + path.getFileName()
                    + "' must have a top-level 'flags' key");
        }
        if (!flagsNode.isObject()) {
            throw new ProviderException("Flag file '" + path.getFileName()
                    + "' has 'flags' that is not an object");
        }
        if (flagsNode.size() == 0) {
            return Collections.emptyMap();
        }

        Map<String, Flag> flags = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = flagsNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode flagNode = entry.getValue();
            flags.put(key, parseFlag(key, flagNode, path));
        }
        return Collections.unmodifiableMap(flags);
    }

    private ObjectMapper resolveMapper(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
            return YAML_MAPPER;
        }
        if (fileName.endsWith(".json")) {
            return JSON_MAPPER;
        }
        throw new ProviderException(
                "Unrecognized file extension for flag file '" + path.getFileName()
                        + "'. Supported extensions: .yml, .yaml, .json");
    }

    private Flag parseFlag(String key, JsonNode node, Path sourcePath) {
        if (!node.hasNonNull("type")) {
            throw new ProviderException("Flag '" + key + "' in '" + sourcePath.getFileName()
                    + "' is missing required field 'type'");
        }
        if (!node.hasNonNull("value")) {
            throw new ProviderException("Flag '" + key + "' in '" + sourcePath.getFileName()
                    + "' is missing required field 'value'");
        }

        FlagType type = parseType(key, node.get("type").asText(), sourcePath);
        FlagValue value = parseValue(key, node.get("value"), type, sourcePath);
        boolean enabled = !node.hasNonNull("enabled") || node.get("enabled").asBoolean(true);

        Map<String, String> metadata = new HashMap<>();
        if (node.hasNonNull("description")) {
            metadata.put("description", node.get("description").asText());
        }

        return new Flag(key, type, value, enabled, metadata);
    }

    private FlagType parseType(String key, String typeStr, Path sourcePath) {
        return switch (typeStr.toLowerCase()) {
            case "boolean" -> FlagType.BOOLEAN;
            case "string" -> FlagType.STRING;
            case "number" -> FlagType.NUMBER;
            case "object" -> FlagType.OBJECT;
            default -> throw new ProviderException("Flag '" + key + "' in '"
                    + sourcePath.getFileName() + "' has unknown type '" + typeStr
                    + "'. Supported: boolean, string, number, object");
        };
    }

    @SuppressWarnings("unchecked")
    private FlagValue parseValue(String key, JsonNode valueNode, FlagType type, Path sourcePath) {
        try {
            return switch (type) {
                case BOOLEAN -> {
                    if (!valueNode.isBoolean()) {
                        throw new ProviderException("Flag '" + key + "' in '"
                                + sourcePath.getFileName() + "' has type 'boolean' but value is not a boolean");
                    }
                    yield FlagValue.of(valueNode.asBoolean(), FlagType.BOOLEAN);
                }
                case STRING -> FlagValue.of(valueNode.asText(), FlagType.STRING);
                case NUMBER -> {
                    if (!valueNode.isNumber()) {
                        throw new ProviderException("Flag '" + key + "' in '"
                                + sourcePath.getFileName() + "' has type 'number' but value is not a number");
                    }
                    yield FlagValue.of(valueNode.asDouble(), FlagType.NUMBER);
                }
                case OBJECT -> {
                    if (!valueNode.isObject()) {
                        throw new ProviderException("Flag '" + key + "' in '"
                                + sourcePath.getFileName() + "' has type 'object' but value is not an object");
                    }
                    Map<String, Object> map = JSON_MAPPER.convertValue(valueNode, Map.class);
                    yield FlagValue.of(map, FlagType.OBJECT);
                }
            };
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException("Failed to parse value for flag '" + key + "' in '"
                    + sourcePath.getFileName() + "'", e);
        }
    }
}
