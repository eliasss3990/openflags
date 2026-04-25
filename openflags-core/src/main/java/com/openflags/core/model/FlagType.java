package com.openflags.core.model;

/**
 * Supported data types for feature flag values.
 */
public enum FlagType {
    /** A true/false flag. */
    BOOLEAN,
    /** A text value flag. */
    STRING,
    /** A numeric (double) flag. */
    NUMBER,
    /** A structured JSON object flag. */
    OBJECT
}
