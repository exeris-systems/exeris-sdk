/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.invoicing.domain;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Field;
import eu.exeris.sdk.annotation.Validation;
import eu.exeris.sdk.annotation.system.AuditCreatedAt;
import eu.exeris.sdk.annotation.system.PrimaryKey;
import eu.exeris.sdk.annotation.system.TenantId;
import eu.exeris.sdk.annotation.system.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * A party that is billed. The smallest useful entity: system fields declared by marker
 * annotation, and {@code @Field} / {@code @Validation} split by role.
 *
 * <p>{@code @Field} says how the attribute behaves in the lifecycle ({@code required},
 * {@code inCreate}, {@code inUpdate}); {@code @Validation} is the only place a constraint is
 * declared.
 *
 * <p>{@code @PrimaryKey} is PARTIAL: the reader carries the marker and the build-time processor
 * does not read it yet. {@code inUpdate} is PARTIAL too: it reaches the metadata, and no generator
 * filters the update payload by it yet.
 */
@ExerisDomain(
        module = "invoicing",
        path = "/customers",
        description = "A party that is billed",
        dataScope = ExerisDomain.DataScope.TENANT)
public class Customer {

    @PrimaryKey
    private UUID id;

    @TenantId
    private UUID tenantId;

    @Version
    private long version;

    @AuditCreatedAt
    private Instant createdAt;

    @Field(label = "Name", required = true, searchable = true)
    @Validation(minLength = 2, maxLength = 120)
    private String name;

    @Field(label = "Billing e-mail", required = true)
    @Validation(maxLength = 254)
    private String email;

    /** Declared as accepted on create and not on update. */
    @Field(label = "Tax id", inUpdate = false)
    @Validation(pattern = "[A-Z0-9]{8,14}")
    private String taxId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }
}
