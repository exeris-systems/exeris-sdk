/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.invoicing;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Field;
import eu.exeris.sdk.annotation.Relationship;
import eu.exeris.sdk.annotation.Validation;
import eu.exeris.sdk.annotation.system.PrimaryKey;
import eu.exeris.sdk.annotation.system.TenantId;
import eu.exeris.sdk.annotation.system.Version;

import java.util.UUID;

/**
 * One priced position on an {@link Invoice}. Amounts are minor currency units, so the
 * constraints are whole-number bounds. {@code @PrimaryKey} is PARTIAL: the reader carries the
 * marker and the build-time processor does not read it yet.
 */
@ExerisDomain(
        module = "invoicing",
        path = "/invoice-lines",
        dataScope = ExerisDomain.DataScope.TENANT)
public class InvoiceLine {

    @PrimaryKey
    private UUID id;

    @TenantId
    private UUID tenantId;

    @Version
    private long version;

    @Relationship(
            targetEntity = Invoice.class,
            displayField = "number",
            relationshipType = Relationship.RelationshipType.MANY_TO_ONE)
    private Invoice invoice;

    @Field(label = "Description", required = true)
    @Validation(maxLength = 200)
    private String description;

    @Field(label = "Quantity", required = true)
    @Validation(min = 1, max = 10_000)
    private int quantity;

    @Field(label = "Unit price (cents)", required = true)
    @Validation(min = 0)
    private long unitPriceCents;
}
