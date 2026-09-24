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

/** A repeatable free block on a check's detail page (text now, images with the documents phase). */
@Entity
@Table(schema = "cases", name = "check_free_sections")
public class CheckFreeSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "check_id", nullable = false)
    private UUID checkId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FreeSectionKind kind;

    @Column(name = "text_value")
    private String textValue;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CheckFreeSection() {
    }

    public CheckFreeSection(UUID checkId, FreeSectionKind kind, String textValue, int sortOrder) {
        this.checkId = checkId;
        this.kind = kind;
        this.textValue = textValue;
        this.sortOrder = sortOrder;
    }

    public void setTextValue(String textValue) {
        this.textValue = textValue;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCheckId() {
        return checkId;
    }

    public FreeSectionKind getKind() {
        return kind;
    }

    public String getTextValue() {
        return textValue;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
