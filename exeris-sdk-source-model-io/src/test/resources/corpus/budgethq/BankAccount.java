/*
 * budgetHQ corpus member — ported from
 * budgetHQ/backend/sync-service/src/main/java/app/budgethq/sync/domain/BankAccount.java
 * (JPA original; Entity-First port per ADR-003). Real field set preserved,
 * including the VARCHAR(3) currency (V20→V21 hotfix codification) and the
 * anti-enum account type (DEC-22). One deliberate divergence: the original
 * carries the connection FK as a bare UUID to keep the JPA layer shallow —
 * the Entity-First port models it as a @Relationship, which is the shape the
 * processor and codegen consume.
 */
package app.budgethq.sync.domain;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Field;
import eu.exeris.sdk.annotation.Relationship;
import eu.exeris.sdk.annotation.Validation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@ExerisDomain(name = "BankAccount", module = "sync", tenantScoped = true,
              description = "One row per bank account discovered under a bank connection")
public class BankAccount {

    private UUID id;

    /** Tenant scope. NOT NULL per DEC-17 second-half mandate. */
    @Field(label = "Tenant", required = true, readOnly = true)
    private UUID tenantId;

    /** Owning connection — ON DELETE CASCADE at the DB layer in the original. */
    @Relationship(displayField = "provider")
    private BankConnection connection;

    /** Tink-issued provider account identifier. NOT NULL, max 256 chars. */
    @Field(label = "Provider account id", required = true)
    @Validation(maxLength = 256)
    private String providerAccountId;

    /** User-visible account name (e.g. "PKO BP Konto Osobiste *4521"). */
    @Field(label = "Name", required = true, searchable = true, sortable = true)
    @Validation(maxLength = 256)
    private String name;

    /** ISO 4217 currency code. VARCHAR(3) per V20→V21 hotfix codification. */
    @Field(label = "Currency", required = true, filterable = true)
    @Validation(pattern = "[A-Z]{3}")
    private String currency;

    /**
     * Tink account-type discriminator (CHECKING, SAVINGS, CREDIT_CARD, …).
     * VARCHAR not enum per DEC-22 STRICT anti-enum — future Tink types land
     * as new string values without schema migration.
     */
    @Field(label = "Type", required = true, filterable = true)
    @Validation(maxLength = 64)
    private String type;

    /** Current balance. NULLABLE — some account types don't expose one. */
    @Field(label = "Balance", sortable = true, readOnly = true)
    private BigDecimal balance;

    /** Timestamp of the last successful sync. NULLABLE until first sync. */
    @Field(label = "Last synced at", readOnly = true, inCreate = false, inUpdate = false)
    private Instant lastSyncedAt;

    @Field(label = "Created at", readOnly = true, inCreate = false, inUpdate = false)
    private Instant createdAt;
}
