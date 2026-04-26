package com.openflags.provider.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.openflags.core.evaluation.rule.Condition;
import com.openflags.core.evaluation.rule.MultiVariantRule;
import com.openflags.core.evaluation.rule.Operator;
import com.openflags.core.evaluation.rule.Rule;
import com.openflags.core.evaluation.rule.SplitRule;
import com.openflags.core.evaluation.rule.TargetingRule;
import com.openflags.core.evaluation.rule.WeightedVariant;
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

    // immutable after static initialization — do not expose references
    private static final ObjectMapper YAML_MAPPER = JsonMapper.builder(new YAMLFactory()).build();
    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().build();

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
        return parseFlags(root, "file:" + path.getFileName());
    }

    /**
     * Parses an already-deserialized JSON tree representing the openflags document
     * into a map of flags. Used by both {@code FileFlagProvider} (after reading
     * a file) and {@code RemoteFlagProvider} (after deserializing an HTTP response).
     *
     * @param root        the root JsonNode; expected shape: {@code { "flags": { ... } }}
     * @param sourceLabel a label used in error messages (e.g. {@code "remote:https://..."} or
     *                    {@code "file:/etc/flags.yml"}); non-null
     * @return an immutable map from flag key to {@link Flag}
     * @throws ProviderException if the document is malformed
     */
    public Map<String, Flag> parseFlags(JsonNode root, String sourceLabel) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            throw new ProviderException("Flag source '" + sourceLabel + "' is empty or invalid");
        }

        JsonNode flagsNode = root.path("flags");
        if (flagsNode.isMissingNode() || flagsNode.isNull()) {
            throw new ProviderException("Flag source '" + sourceLabel
                    + "' must have a top-level 'flags' key");
        }
        if (!flagsNode.isObject()) {
            throw new ProviderException("Flag source '" + sourceLabel
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
            flags.put(key, parseFlag(key, flagNode, sourceLabel));
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

    private Flag parseFlag(String key, JsonNode node, String sourceLabel) {
        if (!node.hasNonNull("type")) {
            throw new ProviderException("Flag '" + key + "' in '" + sourceLabel
                    + "' is missing required field 'type'");
        }
        if (!node.hasNonNull("value")) {
            throw new ProviderException("Flag '" + key + "' in '" + sourceLabel
                    + "' is missing required field 'value'");
        }

        FlagType type = parseType(key, node.get("type").asText(), sourceLabel);
        FlagValue value = parseValue(key, node.get("value"), type, sourceLabel);
        boolean enabled = !node.hasNonNull("enabled") || node.get("enabled").asBoolean(true);

        Map<String, String> metadata = new HashMap<>();
        if (node.hasNonNull("description")) {
            metadata.put("description", node.get("description").asText());
        }

        List<Rule> rules = List.of();
        if (node.hasNonNull("rules")) {
            JsonNode rulesNode = node.get("rules");
            if (!rulesNode.isArray()) {
                throw new ProviderException("Flag '" + key + "' in '" + sourceLabel
                        + "' has 'rules' that is not a list");
            }
            rules = parseRules(rulesNode, type, key, sourceLabel);
        }

        return new Flag(key, type, value, enabled, metadata, rules);
    }

    List<Rule> parseRules(JsonNode rulesNode, FlagType flagType, String flagKey, String sourceLabel) {
        List<Rule> rules = new ArrayList<>();
        for (JsonNode ruleNode : rulesNode) {
            rules.add(parseRule(ruleNode, flagType, flagKey, sourceLabel));
        }
        if (rules.size() > WARN_RULES_PER_FLAG) {
            log.warn("Flag '{}' in '{}' has {} rules which exceeds the recommended limit of {}",
                    flagKey, sourceLabel, rules.size(), WARN_RULES_PER_FLAG);
        }
        return rules;
    }

    Rule parseRule(JsonNode ruleNode, FlagType flagType, String flagKey, String sourceLabel) {
        String name = requireStringField(ruleNode, "name", flagKey, sourceLabel,
                "Rule in flag '" + flagKey + "' in '" + sourceLabel
                        + "' is missing required field 'name'");
        if (name.isBlank()) {
            throw new ProviderException("Rule in flag '" + flagKey + "' in '" + sourceLabel
                    + "' is missing required field 'name'");
        }

        if (!ruleNode.hasNonNull("kind")) {
            throw new ProviderException("Rule '" + name + "' in flag '" + flagKey + "' in '"
                    + sourceLabel + "' is missing required field 'kind'");
        }
        String kind = ruleNode.get("kind").asText().toLowerCase();

        return switch (kind) {
            case "targeting"    -> parseTargetingRule(name, ruleNode, flagType, flagKey, sourceLabel);
            case "split"        -> parseSplitRule(name, ruleNode, flagType, flagKey, sourceLabel);
            case "multivariant" -> parseMultiVariantRule(name, ruleNode, flagType, flagKey, sourceLabel);
            default -> throw new ProviderException("Rule '" + name + "' in flag '" + flagKey + "' in '"
                    + sourceLabel + "' has unknown kind '" + kind
                    + "'. Supported: targeting, split, multivariant");
        };
    }

    private TargetingRule parseTargetingRule(String name, JsonNode ruleNode, FlagType flagType,
            String flagKey, String sourceLabel) {
        if (!ruleNode.hasNonNull("when")) {
            throw new ProviderException("TargetingRule '" + name + "' in flag '" + flagKey + "' in '"
                    + sourceLabel + "' is missing required field 'when'");
        }
        JsonNode whenNode = ruleNode.get("when");
        if (!whenNode.isArray() || whenNode.size() == 0) {
            throw new ProviderException("TargetingRule '" + name + "' in flag '" + flagKey + "' in '"
                    + sourceLabel + "' must have at least one condition");
        }
        if (whenNode.size() > MAX_CONDITIONS_PER_RULE) {
            throw new ProviderException("TargetingRule '" + name + "' in flag '" + flagKey + "' in '"
                    + sourceLabel + "' has " + whenNode.size()
                    + " conditions which exceeds the maximum of " + MAX_CONDITIONS_PER_RULE);
        }

        if (!ruleNode.hasNonNull("value")) {
            throw new ProviderException("Rule '" + name + "' in flag '" + flagKey + "' in '"
                    + sourceLabel + "' is missing required field 'value'");
        }
        FlagValue value = parseValue(flagKey, ruleNode.get("value"), flagType, sourceLabel);

        List<Condition> conditions = new ArrayList<>();
        int i = 0;
        for (JsonNode condNode : whenNode) {
            conditions.add(parseCondition(condNode, i, name, flagKey, sourceLabel));
            i++;
        }

        return new TargetingRule(name, conditions, value);
    }

    private SplitRule parseSplitRule(String name, JsonNode ruleNode, FlagType flagType,
            String flagKey, String sourceLabel) {
        if (!ruleNode.hasNonNull("percentage")) {
            throw new ProviderException("SplitRule '" + name + "' in flag '" + flagKey + "' in '"
                    + sourceLabel + "' is missing required field 'percentage'");
        }
        int percentage = ruleNode.get("percentage").asInt();
        if (percentage < 0 || percentage > 100) {
            throw new ProviderException("SplitRule '" + name + "' in flag '" + flagKey + "' in '"
                    + sourceLabel + "' has percentage " + percentage + " out of range [0, 100]");
        }

        if (!ruleNode.hasNonNull("value")) {
            throw new ProviderException("Rule '" + name + "' in flag '" + flagKey + "' in '"
                    + sourceLabel + "' is missing required field 'value'");
        }
        FlagValue value = parseValue(flagKey, ruleNode.get("value"), flagType, sourceLabel);

        return new SplitRule(name, percentage, value);
    }

    private MultiVariantRule parseMultiVariantRule(String name, JsonNode ruleNode, FlagType flagType,
            String flagKey, String sourceLabel) {
        if (!ruleNode.hasNonNull("variants")) {
            throw new ProviderException("MultiVariantRule '" + name + "' in flag '" + flagKey
                    + "' is missing required field 'variants'");
        }
        JsonNode variantsNode = ruleNode.get("variants");
        if (!variantsNode.isArray()) {
            throw new ProviderException("MultiVariantRule '" + name + "' in flag '" + flagKey
                    + "' field 'variants' must be a list");
        }
        if (variantsNode.size() == 0) {
            throw new ProviderException("MultiVariantRule '" + name + "' in flag '" + flagKey
                    + "' must have at least one variant");
        }
        if (variantsNode.size() > MultiVariantRule.MAX_VARIANTS) {
            throw new ProviderException("MultiVariantRule '" + name + "' in flag '" + flagKey
                    + "' has " + variantsNode.size() + " variants; maximum is " + MultiVariantRule.MAX_VARIANTS);
        }

        List<WeightedVariant> variants = new ArrayList<>();
        int weightSum = 0;
        for (int i = 0; i < variantsNode.size(); i++) {
            JsonNode variantNode = variantsNode.get(i);
            if (!variantNode.hasNonNull("value")) {
                throw new ProviderException("Variant " + i + " in rule '" + name + "' of flag '"
                        + flagKey + "' is missing required field 'value'");
            }
            if (!variantNode.hasNonNull("weight")) {
                throw new ProviderException("Variant " + i + " in rule '" + name + "' of flag '"
                        + flagKey + "' is missing required field 'weight'");
            }
            JsonNode weightNode = variantNode.get("weight");
            if (!weightNode.isInt()) {
                throw new ProviderException("Variant " + i + " in rule '" + name + "' of flag '"
                        + flagKey + "' has non-integer weight '" + weightNode.asText() + "'");
            }
            int weight = weightNode.asInt();
            if (weight < 0 || weight > 100) {
                throw new ProviderException("Variant " + i + " in rule '" + name + "' of flag '"
                        + flagKey + "' has weight " + weight + " out of range [0, 100]");
            }

            FlagValue variantValue = parseVariantValue(flagKey, variantNode.get("value"), flagType,
                    sourceLabel, i, name);
            variants.add(new WeightedVariant(variantValue, weight));
            weightSum += weight;
        }

        if (weightSum != 100) {
            throw new ProviderException("MultiVariantRule '" + name + "' in flag '" + flagKey
                    + "' weights sum to " + weightSum + ", expected 100");
        }

        return new MultiVariantRule(name, variants);
    }

    private FlagValue parseVariantValue(String flagKey, JsonNode valueNode, FlagType flagType,
            String sourceLabel, int variantIndex, String ruleName) {
        FlagValue value = parseValue(flagKey, valueNode, flagType, sourceLabel);
        if (value.getType() != flagType) {
            throw new ProviderException("Variant " + variantIndex + " in rule '" + ruleName
                    + "' of flag '" + flagKey + "' has value type " + value.getType()
                    + " but flag type is " + flagType);
        }
        return value;
    }

    Condition parseCondition(JsonNode condNode, int index, String ruleName, String flagKey, String sourceLabel) {
        String prefix = "Condition " + index + " in rule '" + ruleName + "' of flag '" + flagKey + "' in '"
                + sourceLabel + "'";

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
        Operator operator = parseOperator(condNode.get("operator").asText(), index, ruleName, flagKey, sourceLabel);
        Object expectedValue = parseExpectedValue(condNode.get("value"), operator, index, ruleName, flagKey, sourceLabel);

        return new Condition(attribute, operator, expectedValue);
    }

    Operator parseOperator(String raw, int condIndex, String ruleName, String flagKey, String sourceLabel) {
        try {
            return Operator.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ProviderException("Condition " + condIndex + " in rule '" + ruleName + "' of flag '"
                    + flagKey + "' in '" + sourceLabel + "' has unknown operator '" + raw + "'");
        }
    }

    Object parseExpectedValue(JsonNode valueNode, Operator op, int condIndex, String ruleName,
            String flagKey, String sourceLabel) {
        String prefix = "Condition " + condIndex + " in rule '" + ruleName + "' of flag '" + flagKey + "' in '"
                + sourceLabel + "'";

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

    private FlagType parseType(String key, String typeStr, String sourceLabel) {
        return switch (typeStr.toLowerCase()) {
            case "boolean" -> FlagType.BOOLEAN;
            case "string" -> FlagType.STRING;
            case "number" -> FlagType.NUMBER;
            case "object" -> FlagType.OBJECT;
            default -> throw new ProviderException("Flag '" + key + "' in '"
                    + sourceLabel + "' has unknown type '" + typeStr
                    + "'. Supported: boolean, string, number, object");
        };
    }

    @SuppressWarnings("unchecked")
    private FlagValue parseValue(String key, JsonNode valueNode, FlagType type, String sourceLabel) {
        try {
            return switch (type) {
                case BOOLEAN -> {
                    if (!valueNode.isBoolean()) {
                        throw new ProviderException("Flag '" + key + "' in '"
                                + sourceLabel + "' has type 'boolean' but value is not a boolean");
                    }
                    yield FlagValue.of(valueNode.asBoolean(), FlagType.BOOLEAN);
                }
                case STRING -> FlagValue.of(valueNode.asText(), FlagType.STRING);
                case NUMBER -> {
                    if (!valueNode.isNumber()) {
                        throw new ProviderException("Flag '" + key + "' in '"
                                + sourceLabel + "' has type 'number' but value is not a number");
                    }
                    yield FlagValue.of(valueNode.asDouble(), FlagType.NUMBER);
                }
                case OBJECT -> {
                    if (!valueNode.isObject()) {
                        throw new ProviderException("Flag '" + key + "' in '"
                                + sourceLabel + "' has type 'object' but value is not an object");
                    }
                    Map<String, Object> map = JSON_MAPPER.convertValue(valueNode, Map.class);
                    yield FlagValue.of(map, FlagType.OBJECT);
                }
            };
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException("Failed to parse value for flag '" + key + "' in '"
                    + sourceLabel + "'", e);
        }
    }

    private String requireStringField(JsonNode node, String field, String flagKey, String sourceLabel,
            String message) {
        if (!node.hasNonNull(field)) {
            throw new ProviderException(message);
        }
        return node.get(field).asText();
    }
}
