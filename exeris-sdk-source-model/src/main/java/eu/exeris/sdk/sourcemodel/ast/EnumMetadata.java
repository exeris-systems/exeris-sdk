package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Metadata for enum types used by domain entities.
 * Used by code generators to create proper enum types in target languages.
 *
 * @author Exeris SDK Team
 * @since 0.2.0
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EnumMetadata(
        /** Enum simple name (e.g., "TenantPlan") */
        String name,

        /** Fully qualified name (e.g., "eu.exeris.foundation.domain.TenantPlan") */
        String qualifiedName,

        /** Package name */
        String packageName,

        /** Description from Javadoc */
        String description,

        /** Enum values with their metadata */
        List<EnumValueMetadata> values
) {
    /**
     * Metadata for individual enum value.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record EnumValueMetadata(
            /** Enum constant name (e.g., "FREE") */
            String name,

            /** Display name for UI (e.g., "Free") */
            String displayName,

            /** Description from Javadoc */
            String description,

            /** Ordinal position */
            int ordinal
    ) {}
}

