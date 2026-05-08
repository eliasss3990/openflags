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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
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

    private static final Logger log = LoggerFactory.getLogger(SnapshotWriter.class);

    // Prefix and suffix of the per-write temp file name (see write()).
    // Kept package-visible so tests can assert against the same constants.
    static final String TMP_PREFIX = ".openflags-snapshot-";
    static final String TMP_SUFFIX = ".tmp";
    static final String TMP_GLOB = TMP_PREFIX + "*" + TMP_SUFFIX;

    // Age threshold under which a temp file is considered potentially in-flight
    // (a peer process may be mid-write). Only files older than this are eligible
    // for orphan cleanup. 5 minutes is far longer than any realistic write but
    // short enough that genuine orphans get reaped at the next provider start.
    static final Duration ORPHAN_MIN_AGE = Duration.ofMinutes(5);

    // ObjectMapper is thread-safe after configuration and reused across writes;
    // built once per SnapshotWriter (one per HybridFlagProvider).
    private final ObjectMapper mapper;

    SnapshotWriter(SnapshotFormat format) {
        this.mapper = buildMapper(Objects.requireNonNull(format, "format must not be null"));
    }

    /**
     * Removes orphan temp files left behind by previous JVM crashes during a write.
     * Matches the same naming pattern produced by {@link #write}: hidden files in the
     * snapshot's parent directory whose name starts with {@code .openflags-snapshot-}
     * and ends with {@code .tmp}.
     * <p>Only files older than {@link #ORPHAN_MIN_AGE} are deleted, leaving
     * potentially in-flight writes from a concurrent peer JVM untouched.</p>
     * <p>Best-effort: any I/O error is logged and swallowed so that a transient
     * cleanup failure does not block provider initialization.</p>
     *
     * @param target the snapshot path; only its parent directory is scanned
     */
    void cleanupOrphanTmpFiles(Path target) {
        Objects.requireNonNull(target, "target must not be null");
        Path parent = target.toAbsolutePath().getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return;
        }
        Instant cutoff = Instant.now().minus(ORPHAN_MIN_AGE);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent, TMP_GLOB)) {
            for (Path orphan : stream) {
                try {
                    FileTime mtime = Files.getLastModifiedTime(orphan);
                    if (mtime.toInstant().isAfter(cutoff)) {
                        log.debug("Skipping potentially in-flight snapshot temp file: {}",
                                orphan.getFileName());
                        continue;
                    }
                    Files.deleteIfExists(orphan);
                    log.info("Removed orphan snapshot temp file: {}", orphan.getFileName());
                } catch (IOException e) {
                    log.warn("Failed to delete orphan snapshot temp file '{}': {}",
                            orphan.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Could not scan for orphan snapshot temp files in '{}': {}",
                    parent, e.getMessage());
        }
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
        String tmpName = TMP_PREFIX + UUID.randomUUID() + TMP_SUFFIX;
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
