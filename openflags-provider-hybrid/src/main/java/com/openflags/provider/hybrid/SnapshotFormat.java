package com.openflags.provider.hybrid;

/**
 * Serialization format for the hybrid provider snapshot file.
 */
public enum SnapshotFormat {

    /** JSON; the document matches the structure parsed by {@code FlagFileParser}. */
    JSON,

    /** YAML; same structure as JSON, written via Jackson YAML. */
    YAML
}
