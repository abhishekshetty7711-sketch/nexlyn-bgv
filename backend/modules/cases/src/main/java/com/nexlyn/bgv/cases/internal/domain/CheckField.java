package com.nexlyn.bgv.cases.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * One value of a check. For a sensitive field (Aadhaar, PAN, UAN) {@code value} holds only the masked
 * form and the real number exists only encrypted in {@code valueEncrypted}.
 */
@Entity
@Table(schema = "cases", name = "check_fields")
public class CheckField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "check_id", nullable = false)
    private UUID checkId;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(nullable = false)
    private String label;

    private String value;

    @Column(name = "value_encrypted")
    private String valueEncrypted;

    @Column(name = "value_last4")
    private String valueLast4;

    @Column(name = "verified_tick", nullable = false)
    private boolean verifiedTick;

    @Column(name = "is_manual", nullable = false)
    private boolean manual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldSource source = FieldSource.MANUAL;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CheckField() {
    }

    public CheckField(UUID checkId, String fieldKey, String label, int sortOrder) {
        this.checkId = checkId;
        this.fieldKey = fieldKey;
        this.label = label;
        this.sortOrder = sortOrder;
    }

    /** A plain value, typed by hand or taken from the candidate. */
    public void setPlain(String value, FieldSource source, boolean manual) {
        this.value = value;
        this.valueEncrypted = null;
        this.valueLast4 = null;
        this.source = source;
        this.manual = manual;
    }

    /** A sensitive value: only the masked form is readable; the real one is stored encrypted. */
    public void setSensitive(String masked, String encrypted, String last4) {
        this.value = masked;
        this.valueEncrypted = encrypted;
        this.valueLast4 = last4;
        this.source = FieldSource.MANUAL;
        this.manual = true;
    }

    public void clear() {
        this.value = null;
        this.valueEncrypted = null;
        this.valueLast4 = null;
        this.source = FieldSource.MANUAL;
        this.manual = false;
    }

    public void setVerifiedTick(boolean verifiedTick) {
        this.verifiedTick = verifiedTick;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCheckId() {
        return checkId;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public String getValueEncrypted() {
        return valueEncrypted;
    }

    public String getValueLast4() {
        return valueLast4;
    }

    public boolean isVerifiedTick() {
        return verifiedTick;
    }

    public boolean isManual() {
        return manual;
    }

    public FieldSource getSource() {
        return source;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
