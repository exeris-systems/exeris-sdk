/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.invoicing;

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
}
