package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;

/**
 * Metadata for entity fields defined with @Field annotation.
 *
 * <p>{@code FieldMetadata} is the single canonical AST carrier of the
 * {@code @Validation} constraint values ({@code min} / {@code max} /
 * {@code minLength} / {@code maxLength} / {@code pattern}); DB NOT NULL /
 * not-blank semantics derive from {@link #required()} at generator level.
 *
 * @param name the Java field's name — the field's identity throughout the generated tree
 * @param type the field's Java type, as written in source
 * @param columnName the physical column name; blank means the generator derives it from
 *        {@link #name()}
 * @param displayName the label a generated UI shows for the field
 * @param description human-readable prose for generated documentation and UI help text
 * @param required whether a value must be present. This is the <em>single</em> carrier of the
 *        obligation: database NOT NULL and not-blank checks are derived from it at generator
 *        level rather than declared separately (ADR-054)
 * @param unique whether values must be unique across rows
 * @param indexed whether a database index is generated for the column
 * @param searchable whether the field participates in generated search queries
 * @param sortable whether generated list views may sort on the field
 * @param filterable whether generated list views may filter on the field
 * @param audited whether changes to this field are recorded in the audit trail
 * @param readOnly whether the field is presented but never written by a client
 * @param hidden whether the field is omitted from generated presentation surfaces
 * @param defaultValue the value applied when a client supplies none, as source text
 * @param minLength the minimum string length, from {@code @Validation}. Absent means no bound;
 *        this component carries its own {@code NON_NULL} so a zero bound survives the wire
 * @param maxLength the maximum string length, from {@code @Validation}. Absent means no bound;
 *        carries its own {@code NON_NULL} for the same reason as {@link #minLength()}
 * @param min the inclusive numeric floor, from {@code @Validation}. Absent means no bound;
 *        carries its own {@code NON_NULL}, which is what lets {@code min = 0} — a non-negativity
 *        floor — survive a posture that treats boxed zero as empty
 * @param max the inclusive numeric ceiling, from {@code @Validation}. Absent means no bound;
 *        carries its own {@code NON_NULL} for the same reason as {@link #min()}
 * @param pattern the regular expression a string value must match, from {@code @Validation}.
 *        Unlike the numeric bounds it stays under the record's posture — a string has no
 *        zero-analog hazard, and {@code ""} is correctly dropped
 * @param format the semantic format hint (e.g. an email or URL shape) driving generated
 *        validation and input widgets
 * @param dataType a free-form presentation hint about how the value should be rendered. It is
 *        not a constraint and does not declare storage: {@code @Blob} is what says a field holds
 *        a binary object
 * @param enumType the fully qualified name of the enum the field's values come from, when it is
 *        enum-typed
 * @param computed whether the value is derived rather than stored
 * @param computedFrom the names of the fields a {@link #computed()} value is derived from
 * @param inCreate whether the field is accepted on the generated create surface
 * @param inUpdate whether the field is accepted on the generated update surface
 * @param displayNameKey the message-bundle key for {@link #displayName()}; when set, the literal
 *        display name is the fallback text. Absent when unset
 * @param descriptionKey the message-bundle key for {@link #description()}, on the same terms as
 *        {@link #displayNameKey()}
 * @param derived the declarative derivation facet, present when the field carries a
 *        {@code @Derived} expression and absent for an ordinary stored field
 *
 *        <p>Added in 0.7.0
 * @param blob the binary-object facet, present when the field carries a {@code @Blob}
 *        declaration and absent for an ordinary inline-valued field
 *
 *        <p>Added in 0.11.0. Reserved: no processor populates it and no generator consumes it,
 *        and it is outside the 1.0.0 freeze while the kernel holds {@code …spi.storage.blob} at
 *        tier {@code preview} (ADR-072)
 * @author Exeris SDK Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record FieldMetadata(
        String name,
        String type,
        String columnName,
        String displayName,
        String description,
        boolean required,
        boolean unique,
        boolean indexed,
        boolean searchable,
        boolean sortable,
        boolean filterable,
        boolean audited,
        boolean readOnly,
        boolean hidden,
        String defaultValue,
        // Bounds (0.9.0): per-component NON_NULL — zero is a meaningful bound
        // (e.g. min = 0 as a non-negativity floor) and survives the wire; the
        // class-level NON_DEFAULT would treat boxed zero as "empty" and drop
        // it. Absent means "no bound" (null). pattern stays under NON_DEFAULT:
        // strings have no zero-analog hazard ("" is correctly dropped).
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer minLength,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer maxLength,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long min,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long max,
        String pattern,
        String format,
        String dataType,
        String enumType,
        boolean computed,
        List<String> computedFrom,
        boolean inCreate,
        boolean inUpdate,
        // i18n message keys (optional). Pair with displayName / description:
        // when present, the key resolves against the app message bundle and the
        // literal displayName / description is the fallback text. Null when unset.
        String displayNameKey,
        String descriptionKey,
        // Declarative derivation (0.7.0): present when the field carries a
        // @Derived expression; null for an ordinary stored field. See RFC-2026-06-18.
        DerivedMetadata derived,
        // Binary-object facet (0.11.0): present when the field carries a @Blob
        // declaration; null for an ordinary inline-valued field. Reserved surface —
        // no processor populates it and no generator consumes it, and the kernel
        // holds ...spi.storage.blob at tier preview, so it is excluded from the
        // 1.0.0 freeze and a 1.x minor may still change it. See ADR-072.
        BlobMetadata blob
) {

    /**
     * Compact constructor; applies this record's normalization rules.
     */
    public FieldMetadata {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(type, "type is required");
        if (computedFrom == null) computedFrom = List.of();
    }

    /**
     * Creates a minimal {@code FieldMetadata}, with only the essentials set.
     *
     * @param name the {@code name} the result carries
     * @param type the {@code type} the result carries
     * @return the {@code FieldMetadata}
     */
    public static FieldMetadata simple(String name, String type) {
        return builder(name, type).searchable(true).sortable(true).filterable(true).build();
    }

    /**
     * Creates a required {@code FieldMetadata}.
     *
     * @param name the {@code name} the result carries
     * @param type the {@code type} the result carries
     * @return the {@code FieldMetadata}
     */
    public static FieldMetadata required(String name, String type) {
        return builder(name, type).required(true).searchable(true).sortable(true).filterable(true).build();
    }

    /**
     * Starts a builder for a {@code Builder}.
     *
     * @param name the {@code name} the result carries
     * @param type the {@code type} the result carries
     * @return a new builder
     */
    public static Builder builder(String name, String type) {
        return new Builder(name, type);
    }

    /**
     * Whether a {@code minLength} is declared.
     *
     * @return {@code true} when {@link #minLength()} is present
     */
    @JsonIgnore
    public boolean hasValidation() {
        return minLength != null || maxLength != null || min != null || max != null || pattern != null;
    }

    /**
     * The effective {@code columnName}: the declared value when one is set, and this
     * record's documented fallback otherwise.
     *
     * @return the effective value
     */
    @JsonIgnore
    public String effectiveColumnName() {
        return (columnName != null && !columnName.isBlank()) ? columnName : toSnakeCase(name);
    }

    /**
     * The effective {@code displayName}: the declared value when one is set, and this
     * record's documented fallback otherwise.
     *
     * @return the effective value
     */
    @JsonIgnore
    public String effectiveDisplayName() {
        return (displayName != null && !displayName.isBlank()) ? displayName : name;
    }

    /**
     * Whether a {@code derived} is declared.
     *
     * @return {@code true} when {@link #derived()} is present
     */
    @JsonIgnore
    public boolean hasDerived() { return derived != null; }

    /**
     * Whether a {@code blob} is declared.
     *
     * @return {@code true} when {@link #blob()} is present
     */
    @JsonIgnore
    public boolean hasBlob() { return blob != null; }

    /**
     * Whether a non-blank {@code enumType} is declared.
     *
     * @return {@code true} when {@link #enumType()} is set and not blank
     */
    @JsonIgnore
    public boolean isEnum() { return enumType != null && !enumType.isBlank(); }
    /**
     * Whether the field's declared type is a string.
     *
     * @return {@code isString} as this record reports it
     */
    @JsonIgnore
    public boolean isString() { return "String".equals(type) || "java.lang.String".equals(type); }
    /**
     * Whether the field's declared type is a number, integral or decimal.
     *
     * @return {@code isNumeric} as this record reports it
     */
    @JsonIgnore
    public boolean isNumeric() { return isInteger() || isDecimal(); }
    /**
     * Whether the field's declared type is an integral number.
     *
     * @return {@code isInteger} as this record reports it
     */
    @JsonIgnore
    public boolean isInteger() { return "Integer".equals(type) || "Long".equals(type) || "int".equals(type) || "long".equals(type); }
    /**
     * Whether the field's declared type is a decimal number.
     *
     * @return {@code isDecimal} as this record reports it
     */
    @JsonIgnore
    public boolean isDecimal() { return "BigDecimal".equals(type) || "Double".equals(type) || "Float".equals(type); }
    /**
     * Whether the field's declared type is a boolean.
     *
     * @return {@code isBoolean} as this record reports it
     */
    @JsonIgnore
    public boolean isBoolean() { return "Boolean".equals(type) || "boolean".equals(type); }
    /**
     * Whether the field's declared type is a date, time or instant.
     *
     * @return {@code isTemporal} as this record reports it
     */
    @JsonIgnore
    public boolean isTemporal() { return type.contains("LocalDate") || type.contains("LocalDateTime") || type.contains("Instant") || type.contains("OffsetDateTime"); }

    private static String toSnakeCase(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * A mutable builder for {@code FieldMetadata}.
     *
     * <p>Each setter sets the record component of the same name. Those components are
     * documented by the record's own {@code @param} tags and are deliberately not restated
     * here — a per-setter repetition of the component's meaning is filler, and filler is what
     * makes generated javadoc worth less than none.
     */
    public static final class Builder {
        private final String name;
        private final String type;
        private String columnName;
        private String displayName;
        private String description;
        private boolean required = false;
        private boolean unique = false;
        private boolean indexed = false;
        private boolean searchable = false;
        private boolean sortable = false;
        private boolean filterable = false;
        private boolean audited = false;
        private boolean readOnly = false;
        private boolean hidden = false;
        private String defaultValue;
        private Integer minLength;
        private Integer maxLength;
        private Long min;
        private Long max;
        private String pattern;
        private String format;
        private String dataType;
        private String enumType;
        private boolean computed = false;
        private List<String> computedFrom = List.of();
        private boolean inCreate = true;
        private boolean inUpdate = true;
        private String displayNameKey;
        private String descriptionKey;
        private DerivedMetadata derived;
        private BlobMetadata blob;

        private Builder(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public Builder columnName(String v) { this.columnName = v; return this; }
        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder required(boolean v) { this.required = v; return this; }
        public Builder unique(boolean v) { this.unique = v; return this; }
        public Builder indexed(boolean v) { this.indexed = v; return this; }
        public Builder searchable(boolean v) { this.searchable = v; return this; }
        public Builder sortable(boolean v) { this.sortable = v; return this; }
        public Builder filterable(boolean v) { this.filterable = v; return this; }
        public Builder audited(boolean v) { this.audited = v; return this; }
        public Builder readOnly(boolean v) { this.readOnly = v; return this; }
        public Builder hidden(boolean v) { this.hidden = v; return this; }
        public Builder defaultValue(String v) { this.defaultValue = v; return this; }
        public Builder minLength(Integer v) { this.minLength = v; return this; }
        public Builder maxLength(Integer v) { this.maxLength = v; return this; }
        public Builder min(Long v) { this.min = v; return this; }
        public Builder max(Long v) { this.max = v; return this; }
        public Builder pattern(String v) { this.pattern = v; return this; }
        public Builder format(String v) { this.format = v; return this; }
        // Normalize blank -> null so @Field.dataType's "" default does not survive as
        // "dataType":"" under @JsonInclude(NON_DEFAULT) (whose default is null, not "").
        // dataType is the one string here with an empty-string-defaulted annotation source.
        public Builder dataType(String v) { this.dataType = (v == null || v.isBlank()) ? null : v; return this; }
        public Builder enumType(String v) { this.enumType = v; return this; }
        public Builder computed(boolean v) { this.computed = v; return this; }
        public Builder computedFrom(List<String> v) { this.computedFrom = v != null ? v : List.of(); return this; }
        public Builder inCreate(boolean v) { this.inCreate = v; return this; }
        public Builder inUpdate(boolean v) { this.inUpdate = v; return this; }
        // Normalize blank -> null so the @Field.labelKey/descriptionKey "" defaults
        // do not survive as ""-valued keys under @JsonInclude(NON_DEFAULT).
        public Builder displayNameKey(String v) { this.displayNameKey = (v == null || v.isBlank()) ? null : v; return this; }
        public Builder descriptionKey(String v) { this.descriptionKey = (v == null || v.isBlank()) ? null : v; return this; }
        public Builder derived(DerivedMetadata v) { this.derived = v; return this; }
        public Builder blob(BlobMetadata v) { this.blob = v; return this; }

        /**
         * Builds the {@code FieldMetadata} from this builder's current state.
         *
         * @return the built {@code FieldMetadata}
         */
        public FieldMetadata build() {
            return new FieldMetadata(name, type, columnName, displayName, description, required, unique,
                    indexed, searchable, sortable, filterable, audited, readOnly, hidden, defaultValue,
                    minLength, maxLength, min, max, pattern, format, dataType, enumType, computed, computedFrom,
                    inCreate, inUpdate, displayNameKey, descriptionKey, derived, blob);
        }
    }
}
