package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;

/**
 * Metadata for entity fields defined with @Field annotation.
 *
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
        Integer minLength,
        Integer maxLength,
        Long min,
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
        DerivedMetadata derived
) {

    public FieldMetadata {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(type, "type is required");
        if (computedFrom == null) computedFrom = List.of();
    }

    public static FieldMetadata simple(String name, String type) {
        return builder(name, type).searchable(true).sortable(true).filterable(true).build();
    }

    public static FieldMetadata required(String name, String type) {
        return builder(name, type).required(true).searchable(true).sortable(true).filterable(true).build();
    }

    public static Builder builder(String name, String type) {
        return new Builder(name, type);
    }

    @JsonIgnore
    public boolean hasValidation() {
        return minLength != null || maxLength != null || min != null || max != null || pattern != null;
    }

    @JsonIgnore
    public String effectiveColumnName() {
        return (columnName != null && !columnName.isBlank()) ? columnName : toSnakeCase(name);
    }

    @JsonIgnore
    public String effectiveDisplayName() {
        return (displayName != null && !displayName.isBlank()) ? displayName : name;
    }

    @JsonIgnore
    public boolean hasDerived() { return derived != null; }

    @JsonIgnore
    public boolean isEnum() { return enumType != null && !enumType.isBlank(); }
    @JsonIgnore
    public boolean isString() { return "String".equals(type) || "java.lang.String".equals(type); }
    @JsonIgnore
    public boolean isNumeric() { return isInteger() || isDecimal(); }
    @JsonIgnore
    public boolean isInteger() { return "Integer".equals(type) || "Long".equals(type) || "int".equals(type) || "long".equals(type); }
    @JsonIgnore
    public boolean isDecimal() { return "BigDecimal".equals(type) || "Double".equals(type) || "Float".equals(type); }
    @JsonIgnore
    public boolean isBoolean() { return "Boolean".equals(type) || "boolean".equals(type); }
    @JsonIgnore
    public boolean isTemporal() { return type.contains("LocalDate") || type.contains("LocalDateTime") || type.contains("Instant") || type.contains("OffsetDateTime"); }

    private static String toSnakeCase(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

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

        public FieldMetadata build() {
            return new FieldMetadata(name, type, columnName, displayName, description, required, unique,
                    indexed, searchable, sortable, filterable, audited, readOnly, hidden, defaultValue,
                    minLength, maxLength, min, max, pattern, format, dataType, enumType, computed, computedFrom,
                    inCreate, inUpdate, displayNameKey, descriptionKey, derived);
        }
    }
}
