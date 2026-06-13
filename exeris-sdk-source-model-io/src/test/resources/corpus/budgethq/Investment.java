/*
 * budgetHQ corpus member — ported from
 * budgetHQ/backend/investments-service/src/main/java/app/budgethq/investments/domain/Investment.java
 * (JPA original; Entity-First port per ADR-003). Real field set preserved:
 * NUMERIC(19,4) money fields, the anti-enum assetClass (DEC-22), workspace
 * scoping alongside the tenant.
 */
package app.budgethq.investments.domain;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Field;
import eu.exeris.sdk.annotation.UI;
import eu.exeris.sdk.annotation.Validation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@ExerisDomain(name = "Investment", module = "investments", tenantScoped = true)
@UI(listView = true, detailView = true, createForm = true, editForm = false)
public class Investment {

    private UUID id;

    /** Tenant scope. NOT NULL per DEC-17 second-half mandate. */
    @Field(label = "Tenant", required = true, readOnly = true)
    private UUID tenantId;

    @Field(label = "Name", required = true, searchable = true, sortable = true)
    @Validation(maxLength = 256)
    private String name;

    /** Exchange ticker — nullable for unlisted assets. */
    @Field(label = "Ticker", searchable = true)
    @Validation(maxLength = 16)
    private String ticker;

    /** Asset class discriminator — VARCHAR not enum per DEC-22 STRICT anti-enum. */
    @Field(label = "Asset class", required = true, filterable = true)
    @Validation(maxLength = 64)
    private String assetClass;

    @Field(label = "Quantity", required = true)
    private BigDecimal quantity;

    /** NUMERIC(19,4) per BHQ money-handling convention. */
    @Field(label = "Cost basis")
    private BigDecimal costBasis;

    @Field(label = "Current value", readOnly = true, sortable = true)
    private BigDecimal currentValue;

    @Field(label = "Currency", required = true, filterable = true)
    @Validation(pattern = "[A-Z]{3}")
    private String currency;

    @Field(label = "Active", filterable = true)
    private boolean isActive;

    /** Workspace scope within the tenant. */
    @Field(label = "Workspace", required = true, readOnly = true)
    private UUID workspaceId;

    @Field(label = "Created at", readOnly = true, inCreate = false, inUpdate = false)
    private Instant createdAt;

    @Field(label = "Updated at", readOnly = true, inCreate = false, inUpdate = false)
    private Instant updatedAt;
}
