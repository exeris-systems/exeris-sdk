package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata for system fields (audit, soft-delete, tenant).
 *
 * <p><strong>Build it with {@link #builder()}.</strong> Every component is a {@code String} field
 * name, so a positional call that swaps two of them — {@code createdAtField} for
 * {@code updatedAtField}, say — compiles, round-trips, and silently points a generated column at the
 * wrong field. The builder names each one at the call site. The canonical constructor stays public,
 * and so does the previous release's shape: a record that grows keeps its earlier arity as a
 * delegating constructor ({@code MIGRATION-0.x-to-1.0.md} §3), so neither kind of caller breaks when
 * a component is added.
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
 *        writes pinned; {@code null} on every entity that declares no shared tier
 * @since 0.1
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
     * The 0.11.0 shape — the ten components before {@code sharedScopeField} — kept as a
     * delegating constructor so that code compiled or written against 0.11.0 still links and
     * still compiles. {@code sharedScopeField} is {@code null}, which is what it means on every
     * entity that declares no shared tier.
     *
     * <p>Prefer {@link #builder()}: its setters name what a positional call leaves to argument
     * order.
     *
     * @param primaryKeyField the {@code primaryKeyField} the result carries
     * @param createdAtField the {@code createdAtField} the result carries
     * @param createdByField the {@code createdByField} the result carries
     * @param updatedAtField the {@code updatedAtField} the result carries
     * @param updatedByField the {@code updatedByField} the result carries
     * @param tenantIdField the {@code tenantIdField} the result carries
     * @param versionField the {@code versionField} the result carries
     * @param softDeleteField the {@code softDeleteField} the result carries
     * @param softDeleteTimestampField the {@code softDeleteTimestampField} the result carries
     * @param softDeletedByField the {@code softDeletedByField} the result carries
     */
    public SystemFieldsMetadata(String primaryKeyField, String createdAtField, String createdByField,
                                String updatedAtField, String updatedByField, String tenantIdField,
                                String versionField, String softDeleteField,
                                String softDeleteTimestampField, String softDeletedByField) {
        this(primaryKeyField, createdAtField, createdByField, updatedAtField, updatedByField,
                tenantIdField, versionField, softDeleteField, softDeleteTimestampField,
                softDeletedByField, null);
    }

    /**
     * The default {@code SystemFieldsMetadata}: the canonical field names, and no soft-delete or
     * shared-scope field. The same value {@code builder().build()} returns.
     *
     * @return the {@code SystemFieldsMetadata}
     */
    public static SystemFieldsMetadata defaults() {
        return builder().build();
    }

    /**
     * Starts a builder preset to {@link #defaults()} — {@code id}, {@code createdAt},
     * {@code createdBy}, {@code updatedAt}, {@code updatedBy}, {@code tenantId},
     * {@code version}, and {@code null} for the four optional fields — so a caller sets only
     * the names that differ.
     *
     * @return a new builder
     * @since 0.12
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A mutable builder for {@code SystemFieldsMetadata}, starting from {@link #defaults()}.
     *
     * <p>Each setter sets the record component of the same name. Those components are
     * documented by the record's own {@code @param} tags and are deliberately not restated
     * here. The builder exists for one reason the record's shape makes acute: all eleven
     * components are {@code String}s, so argument order is the only thing a positional call
     * has to tell them apart.
     *
     * @since 0.12
     */
    public static final class Builder {
        private String primaryKeyField = "id";
        private String createdAtField = "createdAt";
        private String createdByField = "createdBy";
        private String updatedAtField = "updatedAt";
        private String updatedByField = "updatedBy";
        private String tenantIdField = "tenantId";
        private String versionField = "version";
        private String softDeleteField;
        private String softDeleteTimestampField;
        private String softDeletedByField;
        private String sharedScopeField;

        private Builder() {
        }

        public Builder primaryKeyField(String v) { this.primaryKeyField = v; return this; }
        public Builder createdAtField(String v) { this.createdAtField = v; return this; }
        public Builder createdByField(String v) { this.createdByField = v; return this; }
        public Builder updatedAtField(String v) { this.updatedAtField = v; return this; }
        public Builder updatedByField(String v) { this.updatedByField = v; return this; }
        public Builder tenantIdField(String v) { this.tenantIdField = v; return this; }
        public Builder versionField(String v) { this.versionField = v; return this; }
        public Builder softDeleteField(String v) { this.softDeleteField = v; return this; }
        public Builder softDeleteTimestampField(String v) { this.softDeleteTimestampField = v; return this; }
        public Builder softDeletedByField(String v) { this.softDeletedByField = v; return this; }
        public Builder sharedScopeField(String v) { this.sharedScopeField = v; return this; }

        /**
         * Builds the {@code SystemFieldsMetadata} from this builder's current state.
         *
         * @return the built {@code SystemFieldsMetadata}
         */
        public SystemFieldsMetadata build() {
            return new SystemFieldsMetadata(
                    primaryKeyField, createdAtField, createdByField,
                    updatedAtField, updatedByField, tenantIdField,
                    versionField, softDeleteField, softDeleteTimestampField,
                    softDeletedByField, sharedScopeField);
        }
    }
}
