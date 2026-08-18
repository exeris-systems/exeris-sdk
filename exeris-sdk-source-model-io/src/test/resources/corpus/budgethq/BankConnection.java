/*
 * budgetHQ corpus member — ported from
 * budgetHQ/backend/sync-service/src/main/java/app/budgethq/sync/domain/BankConnection.java
 * (JPA original; Entity-First port per ADR-003). Real field set and documented
 * semantics (DEC-17 tenant scoping) preserved; JPA annotations replaced by the
 * SDK surface.
 */
package app.budgethq.sync.domain;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Field;
import eu.exeris.sdk.annotation.Validation;

import java.time.Instant;
import java.util.UUID;

@ExerisDomain(module = "sync", tenantScoped = true)
public class BankConnection {

    private UUID id;

    /** Tenant scope. NOT NULL per DEC-17 second-half mandate. */
    @Field(label = "Tenant", required = true, readOnly = true)
    private UUID tenantId;

    /** Aggregator-side provider name (e.g. {@code "tink"}). */
    @Field(label = "Provider", required = true)
    @Validation(maxLength = 64)
    private String provider;

    /**
     * Connection status discriminator ({@code ACTIVE}, {@code EXPIRED},
     * {@code REVOKED}). VARCHAR not enum per DEC-22 STRICT anti-enum.
     */
    @Field(label = "Status", required = true, filterable = true)
    @Validation(maxLength = 32)
    private String status;

    @Field(label = "Created at", readOnly = true, inCreate = false, inUpdate = false)
    private Instant createdAt;
}
