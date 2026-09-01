package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Metadata for enum types used by domain entities.
 * Used by code generators to create proper enum types in target languages.
 *
 * @param name the enum's simple name (e.g. {@code "TenantPlan"})
 * @param qualifiedName the fully qualified name (e.g. {@code "eu.exeris.foundation.domain.TenantPlan"})
 * @param packageName the package the enum is declared in
 * @param description the enum's own javadoc, carried through for generated documentation
 * @param values the constants, in declaration order
 * @author Exeris SDK Team
 * @since 0.2.0
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EnumMetadata(
        String name,
        String qualifiedName,
        String packageName,
        String description,
        List<EnumValueMetadata> values
) {
    /**
     * Metadata for individual enum value.
     *
     * @param name the constant's name (e.g. {@code "FREE"})
     * @param displayName the label a generated UI shows for it (e.g. {@code "Free"})
     * @param description the constant's own javadoc
     * @param ordinal the constant's declaration position
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record EnumValueMetadata(
            String name,
            String displayName,
            String description,
            int ordinal
    ) {}
}

