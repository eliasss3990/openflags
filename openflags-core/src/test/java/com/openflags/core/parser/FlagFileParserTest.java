package com.openflags.core.parser;

import com.openflags.core.evaluation.rule.Condition;
import com.openflags.core.evaluation.rule.Operator;
import com.openflags.core.evaluation.rule.SplitRule;
import com.openflags.core.evaluation.rule.TargetingRule;
import com.openflags.core.exception.ProviderException;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.parser.FlagFileParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

class FlagFileParserTest {

    private FlagFileParser parser;

    @BeforeEach
    void setUp() {
        parser = new FlagFileParser();
    }

    @Test
    void parse_validYaml_returnsAllFlags() {
        Path file = resourcePath("flags-valid.yml");
        Map<String, Flag> flags = parser.parse(file);

        assertThat(flags).containsKeys("dark-mode", "welcome-message", "rollout-rate", "feature-config", "disabled-flag");
        assertThat(flags.get("dark-mode").type()).isEqualTo(FlagType.BOOLEAN);
        assertThat(flags.get("dark-mode").value().asBoolean()).isTrue();
        assertThat(flags.get("dark-mode").enabled()).isTrue();
        assertThat(flags.get("disabled-flag").enabled()).isFalse();
    }

    @Test
    void parse_validYaml_includesMetadata() {
        Path file = resourcePath("flags-valid.yml");
        Flag darkMode = parser.parse(file).get("dark-mode");
        assertThat(darkMode.metadata()).containsEntry("description", "Enables dark mode UI");
    }

    @Test
    void parse_validJson_returnsAllFlags() {
        Path file = resourcePath("flags-valid.json");
        Map<String, Flag> flags = parser.parse(file);

        assertThat(flags).containsKeys("dark-mode", "theme", "rate");
        assertThat(flags.get("theme").type()).isEqualTo(FlagType.STRING);
        assertThat(flags.get("theme").value().asString()).isEqualTo("dark");
    }

    @Test
    void parse_explicitEmptyFlagsObject_returnsEmptyMap() {
        Path file = resourcePath("flags-empty.yml");
        Map<String, Flag> flags = parser.parse(file);
        assertThat(flags).isEmpty();
    }

    @Test
    void parse_completelyEmptyFile_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "");
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("flags.yml");
    }

    @Test
    void parse_yamlWithoutFlagsKey_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "other:\n  not-relevant: true\n");
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("flags");
    }

    @Test
    void parse_yamlWithFlagsKeyButNullValue_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "flags:\n");
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("flags");
    }

    @Test
    void parse_allTypes_parsedCorrectly() {
        Path file = resourcePath("flags-types.yml");
        Map<String, Flag> flags = parser.parse(file);

        assertThat(flags.get("bool-flag").type()).isEqualTo(FlagType.BOOLEAN);
        assertThat(flags.get("string-flag").type()).isEqualTo(FlagType.STRING);
        assertThat(flags.get("number-flag").type()).isEqualTo(FlagType.NUMBER);
        assertThat(flags.get("object-flag").type()).isEqualTo(FlagType.OBJECT);
        assertThat(flags.get("number-flag").value().asNumber()).isEqualTo(42.0);
    }

    @Test
    void parse_invalidType_throwsProviderException() {
        Path file = resourcePath("flags-invalid.yml");
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("unknown-type");
    }

    @Test
    void parse_unrecognizedExtension_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.txt");
        Files.writeString(file, "flags:\n  x:\n    type: boolean\n    value: true\n");
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining(".txt")
                .hasMessageContaining("Unrecognized");
    }

    @Test
    void parse_missingTypeField_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "flags:\n  my-flag:\n    value: true\n");
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("type");
    }

    @Test
    void parse_missingValueField_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "flags:\n  my-flag:\n    type: boolean\n");
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("value");
    }

    @Test
    void parse_returnsUnmodifiableMap() {
        Path file = resourcePath("flags-valid.yml");
        Map<String, Flag> flags = parser.parse(file);
        assertThatThrownBy(() -> flags.put("new-flag", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void parse_enabledDefaultsToTrue(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, "flags:\n  my-flag:\n    type: boolean\n    value: true\n");
        Flag flag = parser.parse(file).get("my-flag");
        assertThat(flag.enabled()).isTrue();
    }

    // Phase 2: rules parsing

    @Test
    void parse_flagWithoutRules_backwardsCompat() {
        Path file = resourcePath("flags-valid.yml");
        Flag darkMode = parser.parse(file).get("dark-mode");
        assertThat(darkMode.rules()).isEmpty();
    }

    @Test
    void parse_targetingRule_parsedCorrectly() {
        Path file = resourcePath("flags-with-rules.yml");
        Flag flag = parser.parse(file).get("feature-x");

        assertThat(flag.rules()).hasSize(1);
        assertThat(flag.rules().get(0)).isInstanceOf(TargetingRule.class);
        TargetingRule rule = (TargetingRule) flag.rules().get(0);
        assertThat(rule.name()).isEqualTo("ar-only");
        assertThat(rule.conditions()).hasSize(1);
        Condition c = rule.conditions().get(0);
        assertThat(c.attribute()).isEqualTo("country");
        assertThat(c.operator()).isEqualTo(Operator.EQ);
        assertThat(c.expectedValue()).isEqualTo("AR");
        assertThat(rule.value().asBoolean()).isTrue();
    }

    @Test
    void parse_splitRule_parsedCorrectly() {
        Path file = resourcePath("flags-with-rules.yml");
        Flag flag = parser.parse(file).get("new-checkout");

        assertThat(flag.rules()).hasSize(1);
        assertThat(flag.rules().get(0)).isInstanceOf(SplitRule.class);
        SplitRule rule = (SplitRule) flag.rules().get(0);
        assertThat(rule.name()).isEqualTo("always-rollout");
        assertThat(rule.percentage()).isEqualTo(100);
        assertThat(rule.value().asBoolean()).isTrue();
    }

    @Test
    void parse_inOperator_parsedAsList() {
        Path file = resourcePath("flags-with-rules.yml");
        Flag flag = parser.parse(file).get("internal-tools");
        TargetingRule rule = (TargetingRule) flag.rules().get(0);
        Condition c = rule.conditions().get(0);
        assertThat(c.operator()).isEqualTo(Operator.IN);
        assertThat(c.expectedValue()).isInstanceOf(List.class);
        List<?> inList = (List<?>) c.expectedValue();
        assertThat(inList).hasSize(3);
        assertThat(inList.get(0)).isEqualTo("user-1");
        assertThat(inList.get(1)).isEqualTo("user-7");
        assertThat(inList.get(2)).isEqualTo("user-42");
    }

    @Test
    void parse_matchesOperator_compilesPattern() {
        Path file = resourcePath("flags-with-rules.yml");
        Flag flag = parser.parse(file).get("beta-feature");
        TargetingRule rule = (TargetingRule) flag.rules().get(0);
        Condition c = rule.conditions().get(0);
        assertThat(c.operator()).isEqualTo(Operator.MATCHES);
        assertThat(c.expectedValue()).isInstanceOf(Pattern.class);
    }

    @Test
    void parse_jsonWithRules_parsedCorrectly() {
        Path file = resourcePath("flags-with-rules.json");
        Flag flag = parser.parse(file).get("premium-banner");
        assertThat(flag.rules()).hasSize(3);
        assertThat(flag.rules().get(0)).isInstanceOf(TargetingRule.class);
        assertThat(flag.rules().get(2)).isInstanceOf(SplitRule.class);
    }

    @Test
    void parse_multipleRules_orderPreserved() {
        Path file = resourcePath("flags-with-rules.yml");
        Flag flag = parser.parse(file).get("premium-banner");
        assertThat(flag.rules()).hasSize(3);
        assertThat(flag.rules().get(0)).isInstanceOf(TargetingRule.class);
        assertThat(((TargetingRule) flag.rules().get(0)).name()).isEqualTo("internal-employees");
        assertThat(flag.rules().get(1)).isInstanceOf(TargetingRule.class);
        assertThat(((TargetingRule) flag.rules().get(1)).name()).isEqualTo("premium-users");
        assertThat(flag.rules().get(2)).isInstanceOf(SplitRule.class);
    }

    @Test
    void parse_operator_caseInsensitive(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, """
                flags:
                  f:
                    type: string
                    value: "no"
                    rules:
                      - name: r
                        kind: targeting
                        when:
                          - attribute: x
                            operator: eq
                            value: "v"
                        value: "yes"
                """);
        Flag flag = parser.parse(file).get("f");
        TargetingRule rule = (TargetingRule) flag.rules().get(0);
        assertThat(rule.conditions().get(0).operator()).isEqualTo(Operator.EQ);
    }

    @Test
    void parse_kind_caseInsensitive(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, """
                flags:
                  f:
                    type: boolean
                    value: false
                    rules:
                      - name: r
                        kind: Targeting
                        when:
                          - attribute: x
                            operator: EQ
                            value: "v"
                        value: true
                """);
        Flag flag = parser.parse(file).get("f");
        assertThat(flag.rules().get(0)).isInstanceOf(TargetingRule.class);
    }

    @Test
    void parse_ruleMissingName_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, """
                flags:
                  f:
                    type: boolean
                    value: false
                    rules:
                      - kind: targeting
                        when:
                          - attribute: x
                            operator: EQ
                            value: "v"
                        value: true
                """);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("name");
    }

    @Test
    void parse_ruleMissingKind_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, """
                flags:
                  f:
                    type: boolean
                    value: false
                    rules:
                      - name: r
                        when:
                          - attribute: x
                            operator: EQ
                            value: "v"
                        value: true
                """);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("kind");
    }

    @Test
    void parse_unknownKind_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, """
                flags:
                  f:
                    type: boolean
                    value: false
                    rules:
                      - name: r
                        kind: unknown
                        value: true
                """);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void parse_targetingRuleMissingWhen_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, """
                flags:
                  f:
                    type: boolean
                    value: false
                    rules:
                      - name: r
                        kind: targeting
                        value: true
                """);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("when");
    }

    @Test
    void parse_splitRuleMissingPercentage_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, """
                flags:
                  f:
                    type: boolean
                    value: false
                    rules:
                      - name: r
                        kind: split
                        value: true
                """);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("percentage");
    }

    @Test
    void parse_splitRuleOutOfRangePercentage_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, """
                flags:
                  f:
                    type: boolean
                    value: false
                    rules:
                      - name: r
                        kind: split
                        percentage: 150
                        value: true
                """);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("percentage");
    }

    @Test
    void parse_conditionMissingAttribute_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, """
                flags:
                  f:
                    type: boolean
                    value: false
                    rules:
                      - name: r
                        kind: targeting
                        when:
                          - operator: EQ
                            value: "v"
                        value: true
                """);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("attribute");
    }

    @Test
    void parse_unknownOperator_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, """
                flags:
                  f:
                    type: boolean
                    value: false
                    rules:
                      - name: r
                        kind: targeting
                        when:
                          - attribute: x
                            operator: UNKNOWN_OP
                            value: "v"
                        value: true
                """);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("UNKNOWN_OP");
    }

    @Test
    void parse_inOperatorWithScalar_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, """
                flags:
                  f:
                    type: boolean
                    value: false
                    rules:
                      - name: r
                        kind: targeting
                        when:
                          - attribute: x
                            operator: IN
                            value: "scalar"
                        value: true
                """);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("IN");
    }

    @Test
    void parse_invalidRegex_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, """
                flags:
                  f:
                    type: boolean
                    value: false
                    rules:
                      - name: r
                        kind: targeting
                        when:
                          - attribute: email
                            operator: MATCHES
                            value: "["
                        value: true
                """);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("invalid regex");
    }

    @Test
    void parse_ruleMissingValue_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, """
                flags:
                  f:
                    type: boolean
                    value: false
                    rules:
                      - name: r
                        kind: split
                        percentage: 50
                """);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("value");
    }

    @Test
    void parse_rulesNodeNotList_throwsProviderException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, """
                flags:
                  f:
                    type: boolean
                    value: false
                    rules: "not-a-list"
                """);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("rules");
    }

    @Test
    void parse_tooManyConditions_throwsProviderException(@TempDir Path tempDir) throws IOException {
        StringBuilder yaml = new StringBuilder("flags:\n  f:\n    type: boolean\n    value: false\n");
        yaml.append("    rules:\n      - name: r\n        kind: targeting\n        when:\n");
        for (int i = 0; i <= FlagFileParser.MAX_CONDITIONS_PER_RULE; i++) {
            yaml.append("          - attribute: attr").append(i)
                    .append("\n            operator: EQ\n            value: \"v\"\n");
        }
        yaml.append("        value: true\n");
        Path file = tempDir.resolve("flags.yml");
        Files.writeString(file, yaml.toString());
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("conditions");
    }

    private Path resourcePath(String name) {
        try {
            return Path.of(getClass().getClassLoader().getResource(name).toURI());
        } catch (Exception e) {
            throw new RuntimeException("Test resource not found: " + name, e);
        }
    }
}
