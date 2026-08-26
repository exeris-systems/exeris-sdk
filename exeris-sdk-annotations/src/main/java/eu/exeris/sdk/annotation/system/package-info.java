/**
 * System field-marker annotations for the Exeris SDK — per-field markers naming
 * an entity's infrastructure fields (tenancy, identity, optimistic locking,
 * soft delete, audit stamps).
 * <p>This package contains annotations for:
 * <ul>
 *   <li>{@link eu.exeris.sdk.annotation.system.TenantId @TenantId} - Tenant isolation key</li>
 *   <li>{@link eu.exeris.sdk.annotation.system.PrimaryKey @PrimaryKey} - Primary key field</li>
 *   <li>{@link eu.exeris.sdk.annotation.system.Version @Version} - Optimistic-locking version</li>
 *   <li>{@link eu.exeris.sdk.annotation.system.SoftDelete @SoftDelete} - Soft-delete flag</li>
 *   <li>{@link eu.exeris.sdk.annotation.system.SoftDeleteTimestamp @SoftDeleteTimestamp} /
 *       {@link eu.exeris.sdk.annotation.system.SoftDeletedBy @SoftDeletedBy} - Soft-delete audit</li>
 *   <li>{@link eu.exeris.sdk.annotation.system.AuditCreatedAt @AuditCreatedAt} /
 *       {@link eu.exeris.sdk.annotation.system.AuditCreatedBy @AuditCreatedBy} /
 *       {@link eu.exeris.sdk.annotation.system.AuditUpdatedAt @AuditUpdatedAt} /
 *       {@link eu.exeris.sdk.annotation.system.AuditUpdatedBy @AuditUpdatedBy} - Audit stamps</li>
 * </ul>
 *
 * <h2>Open-Core status — reserved, not yet consumed</h2>
 * <p>These markers are declared-but-unconsumed: the {@code exeris-tooling}
 * processor does not scan fields for them, so placing one on a field has no
 * generated effect today — the per-annotation "Generated Behavior" javadocs
 * describe the target design, not current output. The <em>live</em>
 * system-field path is the {@code @ExerisDomain} field-name override
 * attributes ({@code tenantIdField}, {@code versionField},
 * {@code softDeleteField}, the audit-field overrides, …) — honoured by the
 * tooling repository + Flyway generators — falling back to the canonical
 * accessor names ({@code id}, {@code tenantId}, {@code version},
 * {@code createdAt}, …) when unset. The markers gain effect when processor
 * field-scanning lands; until then they ship as a reserved surface.
 *
 * @author Exeris SDK Team
 * @version 0.9.0
 * @since 0.9.0
 */
package eu.exeris.sdk.annotation.system;
