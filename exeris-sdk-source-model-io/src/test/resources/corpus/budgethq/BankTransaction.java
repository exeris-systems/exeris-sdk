/*
 * budgetHQ corpus member — ported from
 * budgetHQ/backend/sync-service/src/main/java/app/budgethq/sync/domain/BankTransaction.java
 * (JPA original; Entity-First port per ADR-003). Real field set preserved;
 * the account FK becomes a @Relationship (same divergence as BankAccount's
 * connection — documented there). The CREATE-trigger event mirrors the
 * sync-service "transaction ingested" notification the Phase C handler emits.
 */
package app.budgethq.sync.domain;

import eu.exeris.sdk.annotation.DomainEvent;
import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Field;
import eu.exeris.sdk.annotation.Relationship;
import eu.exeris.sdk.annotation.Validation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@ExerisDomain(module = "sync", tenantScoped = true)
@DomainEvent(trigger = DomainEvent.Trigger.CREATE, topic = "sync.bank-transactions")
public class BankTransaction {

    private UUID id;

    /** Tenant scope. NOT NULL per DEC-17 second-half mandate. */
    @Field(label = "Tenant", required = true, readOnly = true)
    private UUID tenantId;

    /** Owning account. */
    @Relationship(displayField = "name")
    private BankAccount account;

    /** Aggregator-side transaction identifier — dedup key per account. */
    @Field(label = "Provider tx id", required = true, readOnly = true)
    @Validation(maxLength = 256)
    private String providerTxId;

    /** Signed amount — negative for debits. NUMERIC(19,4) money convention. */
    @Field(label = "Amount", required = true, sortable = true)
    private BigDecimal amount;

    /** ISO 4217 currency code, same VARCHAR(3) convention as BankAccount. */
    @Field(label = "Currency", required = true, filterable = true)
    @Validation(pattern = "[A-Z]{3}")
    private String currency;

    @Field(label = "Posted at", required = true, sortable = true)
    private Instant postedAt;

    @Field(label = "Description", searchable = true)
    @Validation(maxLength = 512)
    private String description;

    /** Aggregator's category hint — free text, normalized downstream. */
    @Field(label = "Category hint", filterable = true)
    @Validation(maxLength = 128)
    private String categoryHint;

    @Field(label = "Created at", readOnly = true, inCreate = false, inUpdate = false)
    private Instant createdAt;
}
