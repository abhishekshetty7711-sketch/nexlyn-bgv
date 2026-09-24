package com.nexlyn.bgv.cases.internal.checktype;

import java.util.List;

/**
 * One kind of verification, as defined in {@code check-types/<code>.yml}. The frontend draws the
 * check's form from this, and the server validates saved values against it, so a new field or a new
 * type is a YAML change only.
 */
public record CheckTypeDefinition(
        String code,
        int order,
        String displayName,
        String documentName,
        String iconGroup,
        boolean attestationDefault,
        String verificationTypeDefault,
        List<FieldDefinition> fields,
        List<DetailDefault> details) {

    /** One value the admin fills in. */
    public record FieldDefinition(
            String key,
            String label,
            FieldType type,
            boolean sensitive,
            boolean required,
            /** Candidate property this field is filled from, for example {@code fullName}; null = not prefilled. */
            String prefill,
            /** Label switches to "Guardian's ..." when the candidate's related person is a guardian. */
            boolean labelByParentType,
            List<String> options,
            List<ItemField> itemFields) {
    }

    /** A column of a repeatable field (for example the From / To / Reason of a gap). */
    public record ItemField(String key, String label, FieldType type) {
    }

    /** An extra label / value row every new check of this type starts with. */
    public record DetailDefault(String label, String defaultValue) {
    }

    /**
     * The group used for the summary grid and pagination: identity, court, address, employment and
     * education share a group per kind; every other type is its own group (CLAUDE.md {@literal §6.2}).
     */
    public String groupKey() {
        return "unique".equals(iconGroup) ? code : iconGroup;
    }

    public FieldDefinition field(String key) {
        return fields.stream().filter(f -> f.key().equals(key)).findFirst().orElse(null);
    }
}
