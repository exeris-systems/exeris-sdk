package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata for system fields (audit, soft-delete, tenant).
 *
 * @param primaryKeyField the name of the primary-key field
 * @param createdAtField the name of the creation-timestamp audit field
 * @param createdByField the name of the creating-principal audit field
 * @param updatedAtField the name of the last-update-timestamp audit field
 * @param updatedByField the name of the last-updating-principal audit field
 * @param tenantIdField the name of the tenant discriminator field
 * @param versionField the name of the optimistic-locking version field
 * @param softDeleteField the name of the boolean soft-delete flag field
 * @param softDeleteTimestampField the name of the soft-deletion timestamp field
 * @param softDeletedByField the name of the soft-deleting-principal field
 * @param sharedScopeField the name of the shared-scope key field — the column a
 *        {@code DataScope.UNIVERSE} entity's generated policy compares against the kernel's
 *        shared-scope session variable, widening reads while {@code tenantIdField} keeps
 *        writes pinned. Added in 0.12.0; {@code null} on every entity that declares no
 *        shared tier
 * @author Exeris SDK Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SystemFieldsMetadata(
        String primaryKeyField,
        String createdAtField,
        String createdByField,
        String updatedAtField,
        String updatedByField,
        String tenantIdField,
        String versionField,
        String softDeleteField,
        String softDeleteTimestampField,
        String softDeletedByField,
        String sharedScopeField
) {
    /**
     * The default {@code SystemFieldsMetadata}.
     *
     * @return the {@code SystemFieldsMetadata}
     */
    public static SystemFieldsMetadata defaults() {
        return new SystemFieldsMetadata(
                "id", "createdAt", "createdBy",
                "updatedAt", "updatedBy", "tenantId",
                "version", null, null, null,
                null
        );
    }
}

