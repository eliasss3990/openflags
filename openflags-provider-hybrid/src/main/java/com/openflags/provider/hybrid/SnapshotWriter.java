package com.openflags.provider.hybrid;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.openflags.core.evaluation.rule.Condition;
import com.openflags.core.evaluation.rule.MultiVariantRule;
import com.openflags.core.evaluation.rule.Operator;
import com.openflags.core.evaluation.rule.Rule;
import com.openflags.core.evaluation.rule.SplitRule;
import com.openflags.core.evaluation.rule.TargetingRule;
import com.openflags.core.evaluation.rule.WeightedVariant;
import com.openflags.core.model.Flag;
import com.openflags.core.model.FlagType;
import com.openflags.core.model.FlagValue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Serializes a flag map to disk atomically using the write-to-temp + ATOMIC_MOVE pattern.
 * <p>
 * The output document matches the schema parsed by
 * {@code com.openflags.provider.file.FlagFileParser}, so a snapshot can be re-read by a
 * {@code FileFlagProvider} without conversion.
 * </p>
 * <p>Stateless and thread-safe.</p>
 */
final class SnapshotWriter {

    private final ObjectMapper mapper;

    SnapshotWriter(SnapshotFormat format) {
        this.mapper = buildMapper(Objects.requireNonNull(format, "format must not be null"));
    }

    /**
     * Writes the given flags atomically to {@code target}.
     *
     * @param flags  the map to persist; non-null
     * @param target the destination path; non-null
     * @throws IOException          on any I/O failure
     * @throws NullPointerException if either argument is null
     */
    void write(Map<String, Flag> flags, Path target) throws IOException {
        Objects.requireNonNull(flags, "flags must not be null");
        Objects.requireNonNull(target, "target must not be null");

        Path parent = target.toAbsolutePath().getParent();
        String tmpName = ".openflags-snapshot-" + UUID.randomUUID() + ".tmp";
        Path tmp = parent.resolve(tmpName);

        byte[] bytes = serialize(flags);

        try {
            try (OutputStream os = Files.newOutputStream(tmp,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                os.write(bytes);
                os.flush();
            }
            try {
                Files.move(tmp, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // secondary failure — best effort cleanup
            }
            throw e;
        }
    }

    private byte[] serialize(Map<String, Flag> flags) throws IOException {
        Map<String, Flag> sorted = new TreeMap<>(flags);
        return mapper.writeValueAsBytes(Map.of("flags", sorted));
    }

    private static ObjectMapper buildMapper(SnapshotFormat format) {
        ObjectMapper m = (format == SnapshotFormat.YAML)
                ? new ObjectMapper(new YAMLFactory())
                : JsonMapper.builder().build();
        SimpleModule mod = new SimpleModule("openflags-snapshot");
        mod.addSerializer(Flag.class, new FlagSerializer());
        mod.addSerializer(Rule.class, new RuleSerializer());
        mod.addSerializer(FlagValue.class, new FlagValueSerializer());
        m.registerModule(mod);
        return m;
    }

    // -------------------------------------------------------------------------
    // Serializers — emit field names that FlagFileParser expects
    // -------------------------------------------------------------------------

    private static final class FlagSerializer extends StdSerializer<Flag> {

        FlagSerializer() {
            super(Flag.class);
        }

        @Override
        public void serialize(Flag flag, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            gen.writeStartObject();
            gen.writeStringField("type", flag.type().name().toLowerCase());
            gen.writeFieldName("value");
            writeFlagValue(gen, flag.value(), flag.type());
            gen.writeBooleanField("enabled", flag.enabled());
            String desc = flag.metadata().get("description");
            if (desc != null) {
                gen.writeStringField("description", desc);
            }
            if (!flag.rules().isEmpty()) {
                gen.writeArrayFieldStart("rules");
                for (Rule rule : flag.rules()) {
                    provider.defaultSerializeValue(rule, gen);
                }
                gen.writeEndArray();
            }
            gen.writeEndObject();
        }
    }

    private static final class RuleSerializer extends StdSerializer<Rule> {

        RuleSerializer() {
            super(Rule.class);
        }

        @Override
        public void serialize(Rule rule, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            gen.writeStartObject();
            switch (rule) {
                case TargetingRule t -> serializeTargeting(t, gen);
                case SplitRule s -> serializeSplit(s, gen);
                case MultiVariantRule m -> serializeMultiVariant(m, gen);
            }
            gen.writeEndObject();
        }

        private void serializeTargeting(TargetingRule t, JsonGenerator gen)
                throws IOException {
            gen.writeStringField("name", t.name());
            gen.writeStringField("kind", "targeting");
            gen.writeArrayFieldStart("when");
            for (Condition c : t.conditions()) {
                serializeCondition(c, gen);
            }
            gen.writeEndArray();
            gen.writeFieldName("value");
            writeFlagValue(gen, t.value(), t.value().getType());
        }

        private void serializeSplit(SplitRule s, JsonGenerator gen)
                throws IOException {
            gen.writeStringField("name", s.name());
            gen.writeStringField("kind", "split");
            gen.writeNumberField("percentage", s.percentage());
            gen.writeFieldName("value");
            writeFlagValue(gen, s.value(), s.value().getType());
        }

        private void serializeMultiVariant(MultiVariantRule m, JsonGenerator gen)
                throws IOException {
            gen.writeStringField("name", m.name());
            gen.writeStringField("kind", "multivariant");
            gen.writeArrayFieldStart("variants");
            for (WeightedVariant v : m.variants()) {
                gen.writeStartObject();
                gen.writeFieldName("value");
                writeFlagValue(gen, v.value(), v.value().getType());
                gen.writeNumberField("weight", v.weight());
                gen.writeEndObject();
            }
            gen.writeEndArray();
        }

        private void serializeCondition(Condition c, JsonGenerator gen) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("attribute", c.attribute());
            gen.writeStringField("operator", c.operator().name());
            gen.writeFieldName("value");
            writeConditionValue(gen, c.operator(), c.expectedValue());
            gen.writeEndObject();
        }

        private void writeConditionValue(JsonGenerator gen, Operator op, Object expected)
                throws IOException {
            switch (op) {
                case IN, NOT_IN -> {
                    if (!(expected instanceof List<?> items)) {
                        throw new IllegalStateException(
                                "IN/NOT_IN operator expects a List, got "
                                        + (expected == null ? "null" : expected.getClass().getName()));
                    }
                    gen.writeStartArray();
                    for (Object item : items) {
                        if (item instanceof Number n) {
                            gen.writeNumber(n.doubleValue());
                        } else {
                            gen.writeString(item.toString());
                        }
                    }
                    gen.writeEndArray();
                }
                case MATCHES -> gen.writeString(((Pattern) expected).pattern());
                default -> {
                    if (expected instanceof Boolean b) {
                        gen.writeBoolean(b);
                    } else if (expected instanceof Number n) {
                        gen.writeNumber(n.doubleValue());
                    } else {
                        gen.writeString(expected.toString());
                    }
                }
            }
        }
    }

    private static final class FlagValueSerializer extends StdSerializer<FlagValue> {

        FlagValueSerializer() {
            super(FlagValue.class);
        }

        @Override
        public void serialize(FlagValue value, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            writeFlagValue(gen, value, value.getType());
        }
    }

    static void writeFlagValue(JsonGenerator gen, FlagValue value, FlagType type) throws IOException {
        switch (type) {
            case BOOLEAN -> gen.writeBoolean(value.asBoolean());
            case STRING -> gen.writeString(value.asString());
            case NUMBER -> gen.writeNumber(value.asNumber());
            case OBJECT -> gen.writeObject(value.asObject());
        }
    }
}
