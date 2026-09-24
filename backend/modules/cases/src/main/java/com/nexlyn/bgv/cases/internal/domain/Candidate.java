package com.nexlyn.bgv.cases.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The person being verified (one per case). Every check pre-fills from here (Phase 4). */
@Entity
@Table(schema = "cases", name = "candidates")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "full_name")
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "parent_type", nullable = false)
    private ParentType parentType = ParentType.FATHER;

    @Column(name = "parent_name")
    private String parentName;

    @Column(name = "employee_id")
    private String employeeId;

    private LocalDate dob;

    /** Stored as {@code +91XXXXXXXXXX}. */
    private String phone;

    @Column(name = "photo_document_id")
    private UUID photoDocumentId;

    private String street;
    private String city;
    private String state;
    private String pin;

    @Column(nullable = false)
    private String country = "India";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Candidate() {
    }

    public Candidate(UUID caseId) {
        this.caseId = caseId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void apply(String fullName, ParentType parentType, String parentName, String employeeId, LocalDate dob,
                      String phone, String street, String city, String state, String pin, String country) {
        this.fullName = fullName;
        this.parentType = parentType;
        this.parentName = parentName;
        this.employeeId = employeeId;
        this.dob = dob;
        this.phone = phone;
        this.street = street;
        this.city = city;
        this.state = state;
        this.pin = pin;
        this.country = country;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public String getFullName() {
        return fullName;
    }

    public ParentType getParentType() {
        return parentType;
    }

    public String getParentName() {
        return parentName;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public LocalDate getDob() {
        return dob;
    }

    public String getPhone() {
        return phone;
    }

    public UUID getPhotoDocumentId() {
        return photoDocumentId;
    }

    public String getStreet() {
        return street;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPin() {
        return pin;
    }

    public String getCountry() {
        return country;
    }
}
