package com.nexlyn.bgv.cases.internal.checktype;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** The kinds of field a check type can have (CLAUDE.md {@literal §8}). Written in lower case in YAML and JSON. */
public enum FieldType {
    TEXT, TEXTAREA, DATE, NUMBER, PIN, PHONE, AADHAAR, PAN, UAN, BOOLEAN, SELECT, REPEATABLE;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static FieldType fromJson(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
