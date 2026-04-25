package com.openflags.provider.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.openflags.core.evaluation.rule.Condition;
import com.openflags.core.evaluation.rule.Operator;
import com.openflags.core.evaluation.rule.Rule;
import com.openflags.core.evaluation.rule.SplitRule;
import com.openflags.core.evaluation.rule.TargetingRule;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Parses feature flag definition files in YAML or JSON format.
 * <p>
 * The file format is detected strictly by extension:
 * {@code .yml} and {@code .yaml} are parsed as YAML; {@code .json} as JSON.
 * Any other extension causes a {@link ProviderException} with a descriptive message.
 * </p>
 *
 * <h2>Expected file structure (YAML example)</h2>
 * <pre>
 * flags:
 *   dark-mode:
 *     type: boolean
 *     value: true
 *     enabled: true          # optional, defaults to true
 *     description: "..."     # optional metadata
 *     rules:                 # optional, Phase 2
 *       - name: "ar-only"
 *         kind: targeting
 *         when:
 *           - attribute: country
 *             operator: EQ
 *             value: "AR"
 *         value: true
 * </pre>
 */
public final class FlagFileParser {

    private static final Logger log = LoggerFactory.getLogger(FlagFileParser.class);

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** Maximum regex pattern length accepted at parse time (security: limits ReDoS surface). */
    static final int MAX_REGEX_LENGTH = 1024;
    /** Warn if a flag has more than this many rules (soft limit). */
    static final int WARN_RULES_PER_FLAG = 50;
    /** Hard limit: a targeting rule may not have more than this many conditions. */
    static final int MAX_CONDITIONS_PER_RULE = 20;

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

        List<Rule> rules = List.of();
        if (node.hasNonNull("rules")) {
            JsonNode rulesNode = node.get("rules");
            if (!rulesNode.isArray()) {
                throw new ProviderException("Flag '" + key + "' in '" + sourcePath.getFileName()
                        + "' has 'rules' that is not a list");
            }
            rules = parseRules(rulesNode, type, key, sourcePath);
        }

        return new Flag(key, type, value, enabled, metadata, rules);
    }

    List<Rule> parseRules(JsonNode rulesNode, FlagType flagType, String flagKey, Path src) {
        List<Rule> rules = new ArrayList<>();
        for (JsonNode ruleNode : rulesNode) {
            rules.add(parseRule(ruleNode, flagType, flagKey, src));
        }
        if (rules.size() > WARN_RULES_PER_FLAG) {
            log.warn("Flag '{}' in '{}' has {} rules which exceeds the recommended limit of {}",
                    flagKey, src.getFileName(), rules.size(), WARN_RULES_PER_FLAG);
        }
        return rules;
    }

    Rule parseRule(JsonNode ruleNode, FlagType flagType, String flagKey, Path src) {
        String name = requireStringField(ruleNode, "name", flagKey, src,
                "Rule in flag '" + flagKey + "' in '" + src.getFileName()
                        + "' is missing required field 'name'");
        if (name.isBlank()) {
            throw new ProviderException("Rule in flag '" + flagKey + "' in '" + src.getFileName()
                    + "' is missing required field 'name'");
        }

        if (!ruleNode.hasNonNull("kind")) {
            throw new ProviderException("Rule '" + name + "' in flag '" + flagKey + "' in '"
                    + src.getFileName() + "' is missing required field 'kind'");
        }
        String kind = ruleNode.get("kind").asText().toLowerCase();

        return switch (kind) {
            case "targeting" -> parseTargetingRule(name, ruleNode, flagType, flagKey, src);
            case "split" -> parseSplitRule(name, ruleNode, flagType, flagKey, src);
            default -> throw new ProviderException("Rule '" + name + "' in flag '" + flagKey + "' in '"
                    + src.getFileName() + "' has unknown kind '" + kind
                    + "'. Supported: targeting, split");
        };
    }

    private TargetingRule parseTargetingRule(String name, JsonNode ruleNode, FlagType flagType,
            String flagKey, Path src) {
        if (!ruleNode.hasNonNull("when")) {
            throw new ProviderException("TargetingRule '" + name + "' in flag '" + flagKey + "' in '"
                    + src.getFileName() + "' is missing required field 'when'");
        }
        JsonNode whenNode = ruleNode.get("when");
        if (!whenNode.isArray() || whenNode.size() == 0) {
            throw new ProviderException("TargetingRule '" + name + "' in flag '" + flagKey + "' in '"
                    + src.getFileName() + "' must have at least one condition");
        }
        if (whenNode.size() > MAX_CONDITIONS_PER_RULE) {
            throw new ProviderException("TargetingRule '" + name + "' in flag '" + flagKey + "' in '"
                    + src.getFileName() + "' has " + whenNode.size()
                    + " conditions which exceeds the maximum of " + MAX_CONDITIONS_PER_RULE);
        }

        if (!ruleNode.hasNonNull("value")) {
            throw new ProviderException("Rule '" + name + "' in flag '" + flagKey + "' in '"
                    + src.getFileName() + "' is missing required field 'value'");
        }
        FlagValue value = parseValue(flagKey, ruleNode.get("value"), flagType, src);

        List<Condition> conditions = new ArrayList<>();
        int i = 0;
        for (JsonNode condNode : whenNode) {
            conditions.add(parseCondition(condNode, i, name, flagKey, src));
            i++;
        }

        return new TargetingRule(name, conditions, value);
    }

    private SplitRule parseSplitRule(String name, JsonNode ruleNode, FlagType flagType,
            String flagKey, Path src) {
        if (!ruleNode.hasNonNull("percentage")) {
            throw new ProviderException("SplitRule '" + name + "' in flag '" + flagKey + "' in '"
                    + src.getFileName() + "' is missing required field 'percentage'");
        }
        int percentage = ruleNode.get("percentage").asInt();
        if (percentage < 0 || percentage > 100) {
            throw new ProviderException("SplitRule '" + name + "' in flag '" + flagKey + "' in '"
                    + src.getFileName() + "' has percentage " + percentage + " out of range [0, 100]");
        }

        if (!ruleNode.hasNonNull("value")) {
            throw new ProviderException("Rule '" + name + "' in flag '" + flagKey + "' in '"
                    + src.getFileName() + "' is missing required field 'value'");
        }
        FlagValue value = parseValue(flagKey, ruleNode.get("value"), flagType, src);

        return new SplitRule(name, percentage, value);
    }

    Condition parseCondition(JsonNode condNode, int index, String ruleName, String flagKey, Path src) {
        String prefix = "Condition " + index + " in rule '" + ruleName + "' of flag '" + flagKey + "' in '"
                + src.getFileName() + "'";

        if (!condNode.hasNonNull("attribute")) {
            throw new ProviderException(prefix + " is missing required field 'attribute'");
        }
        if (!condNode.hasNonNull("operator")) {
            throw new ProviderException(prefix + " is missing required field 'operator'");
        }
        if (!condNode.hasNonNull("value")) {
            throw new ProviderException(prefix + " is missing required field 'value'");
        }

        String attribute = condNode.get("attribute").asText();
        Operator operator = parseOperator(condNode.get("operator").asText(), index, ruleName, flagKey, src);
        Object expectedValue = parseExpectedValue(condNode.get("value"), operator, index, ruleName, flagKey, src);

        return new Condition(attribute, operator, expectedValue);
    }

    Operator parseOperator(String raw, int condIndex, String ruleName, String flagKey, Path src) {
        try {
            return Operator.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ProviderException("Condition " + condIndex + " in rule '" + ruleName + "' of flag '"
                    + flagKey + "' in '" + src.getFileName() + "' has unknown operator '" + raw + "'");
        }
    }

    Object parseExpectedValue(JsonNode valueNode, Operator op, int condIndex, String ruleName,
            String flagKey, Path src) {
        String prefix = "Condition " + condIndex + " in rule '" + ruleName + "' of flag '" + flagKey + "' in '"
                + src.getFileName() + "'";

        return switch (op) {
            case IN, NOT_IN -> {
                if (!valueNode.isArray()) {
                    throw new ProviderException(prefix + " uses IN but value is not a list");
                }
                List<Object> list = new ArrayList<>();
                for (JsonNode item : valueNode) {
                    if (item.isNumber()) {
                        list.add(item.asDouble());
                    } else {
                        list.add(item.asText());
                    }
                }
                yield List.copyOf(list);
            }
            case MATCHES -> {
                String regexStr = valueNode.asText();
                if (regexStr.length() > MAX_REGEX_LENGTH) {
                    throw new ProviderException(prefix + " has regex longer than " + MAX_REGEX_LENGTH
                            + " characters");
                }
                try {
                    yield Pattern.compile(regexStr);
                } catch (PatternSyntaxException e) {
                    throw new ProviderException(prefix + " has invalid regex '" + regexStr + "': "
                            + e.getDescription());
                }
            }
            case GT, GTE, LT, LTE -> {
                if (!valueNode.isNumber()) {
                    throw new ProviderException(prefix + " uses " + op + " but value is not a number");
                }
                yield valueNode.asDouble();
            }
            default -> {
                if (valueNode.isNumber()) {
                    yield valueNode.asDouble();
                } else if (valueNode.isBoolean()) {
                    yield valueNode.asBoolean();
                } else {
                    yield valueNode.asText();
                }
            }
        };
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

    private String requireStringField(JsonNode node, String field, String flagKey, Path src,
            String message) {
        if (!node.hasNonNull(field)) {
            throw new ProviderException(message);
        }
        return node.get(field).asText();
    }
}
