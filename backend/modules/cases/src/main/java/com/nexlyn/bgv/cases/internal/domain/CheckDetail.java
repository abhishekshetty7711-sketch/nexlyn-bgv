package com.nexlyn.bgv.cases.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** An extra label / value row in a check's "Details" grid (for example Court Type). */
@Entity
@Table(schema = "cases", name = "check_details")
public class CheckDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "check_id", nullable = false)
    private UUID checkId;

    @Column(nullable = false)
    private String label;

    private String value;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CheckDetail() {
    }

    public CheckDetail(UUID checkId, String label, String value, int sortOrder) {
        this.checkId = checkId;
        this.label = label;
        this.value = value;
        this.sortOrder = sortOrder;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
