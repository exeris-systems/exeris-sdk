/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.invoicing;

import eu.exeris.sdk.annotation.Action;
import eu.exeris.sdk.annotation.ActionParam;
import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Field;
import eu.exeris.sdk.annotation.QueryParam;
import eu.exeris.sdk.annotation.Relationship;
import eu.exeris.sdk.annotation.RouteAccess;
import eu.exeris.sdk.annotation.Validation;
import eu.exeris.sdk.annotation.system.AuditCreatedAt;
import eu.exeris.sdk.annotation.system.PrimaryKey;
import eu.exeris.sdk.annotation.system.TenantId;
import eu.exeris.sdk.annotation.system.Version;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A bill issued to a {@link Customer}, with relationships in both directions and two actions.
 *
 * <p>{@code @RouteAccess} is RESERVED: the SDK carries the declaration into the metadata, and a
 * route policy is emitted from it only by a tooling release that reads it. {@code @PrimaryKey},
 * {@code @QueryParam} and {@code inUpdate} are PARTIAL: the reader carries them and the build-time
 * processor does not read them yet.
 */
@ExerisDomain(
        module = "invoicing",
        path = "/invoices",
        description = "A bill issued to a customer",
        dataScope = ExerisDomain.DataScope.TENANT,
        audited = true)
public class Invoice {

    @PrimaryKey
    private UUID id;

    @TenantId
    private UUID tenantId;

    @Version
    private long version;

    @AuditCreatedAt
    private Instant createdAt;

    /** Assigned by the server rather than supplied by the client. */
    @Field(label = "Number", required = true, inCreate = false, inUpdate = false,
            readOnly = true, unique = true)
    private String number;

    @Relationship(
            targetEntity = Customer.class,
            displayField = "name",
            relationshipType = Relationship.RelationshipType.MANY_TO_ONE)
    private Customer customer;

    @Relationship(
            targetEntity = InvoiceLine.class,
            displayField = "description",
            relationshipType = Relationship.RelationshipType.ONE_TO_MANY,
            mappedBy = "invoice",
            cascadeDelete = true)
    private List<InvoiceLine> lines;

    @Field(label = "Total (cents)", readOnly = true)
    @Validation(min = 0)
    private long totalCents;

    /** Moves the invoice from draft to issued. */
    @Action(name = "issue", label = "Issue", description = "Issue a draft invoice")
    public void issue(
            @ActionParam(label = "Due in days", required = false, defaultValue = "30") int dueInDays) {
    }

    /**
     * Lets a customer fetch an invoice by its payment token without an account.
     *
     * <p>Public because the token in the link is the credential; every other action here
     * leaves {@code routeAccess} undeclared.
     */
    @Action(name = "viewShared", label = "View shared invoice", httpMethod = "GET")
    @RouteAccess(RouteAccess.Level.PUBLIC)
    public Invoice viewShared(
            @QueryParam(value = "token", description = "Payment link token") String shareToken) {
        return this;
    }
}
