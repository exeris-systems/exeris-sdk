package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata for system fields (audit, soft-delete, tenant).
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
        String softDeletedByField
) {
    public static SystemFieldsMetadata defaults() {
        return new SystemFieldsMetadata(
                "id", "createdAt", "createdBy",
                "updatedAt", "updatedBy", "tenantId",
                "version", null, null, null
        );
    }
}

