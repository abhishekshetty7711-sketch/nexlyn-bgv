package com.nexlyn.bgv.auth.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A permission code and its description. The set is fixed by the code (see {@code Permission}). */
@Entity
@Table(schema = "auth", name = "permissions")
public class PermissionEntity {

    @Id
    private String code;

    @Column(nullable = false)
    private String description;

    protected PermissionEntity() {
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
