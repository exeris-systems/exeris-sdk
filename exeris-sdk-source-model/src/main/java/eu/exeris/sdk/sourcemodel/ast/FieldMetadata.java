package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Metadata for entity fields defined with @Field annotation.
 * Migrated from {@code com.corelio.sdk.processor.model.FieldMetadata}.
 *
 * @author Exeris Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class FieldMetadata {

    private final String name;
    private final String type;
    private final String columnName;
    private final String displayName;
    private final String description;
    private final boolean required;
    private final boolean unique;
    private final boolean indexed;
    private final boolean searchable;
    private final boolean sortable;
    private final boolean filterable;
    private final boolean audited;
    private final boolean readOnly;
    private final boolean hidden;
    private final String defaultValue;
    private final Integer minLength;
    private final Integer maxLength;
    private final Long min;
    private final Long max;
    private final String pattern;
    private final String format;
    private final String enumType;
    private final boolean computed;
    private final List<String> computedFrom;
    private final boolean inCreate;
    private final boolean inUpdate;

    @JsonCreator
    public FieldMetadata(
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("columnName") String columnName,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("description") String description,
            @JsonProperty("required") boolean required,
            @JsonProperty("unique") boolean unique,
            @JsonProperty("indexed") boolean indexed,
            @JsonProperty("searchable") boolean searchable,
            @JsonProperty("sortable") boolean sortable,
            @JsonProperty("filterable") boolean filterable,
            @JsonProperty("audited") boolean audited,
            @JsonProperty("readOnly") boolean readOnly,
            @JsonProperty("hidden") boolean hidden,
            @JsonProperty("defaultValue") String defaultValue,
            @JsonProperty("minLength") Integer minLength,
            @JsonProperty("maxLength") Integer maxLength,
            @JsonProperty("min") Long min,
            @JsonProperty("max") Long max,
            @JsonProperty("pattern") String pattern,
            @JsonProperty("format") String format,
            @JsonProperty("enumType") String enumType,
            @JsonProperty("computed") boolean computed,
            @JsonProperty("computedFrom") List<String> computedFrom,
            @JsonProperty("inCreate") boolean inCreate,
            @JsonProperty("inUpdate") boolean inUpdate) {
        this.name = Objects.requireNonNull(name, "name is required");
        this.type = Objects.requireNonNull(type, "type is required");
        this.columnName = columnName;
        this.displayName = displayName;
        this.description = description;
        this.required = required;
        this.unique = unique;
        this.indexed = indexed;
        this.searchable = searchable;
        this.sortable = sortable;
        this.filterable = filterable;
        this.audited = audited;
        this.readOnly = readOnly;
        this.hidden = hidden;
        this.defaultValue = defaultValue;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.min = min;
        this.max = max;
        this.pattern = pattern;
        this.format = format;
        this.enumType = enumType;
        this.computed = computed;
        this.computedFrom = computedFrom != null ? computedFrom : List.of();
        this.inCreate = inCreate;
        this.inUpdate = inUpdate;
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

    // Getters
    @JsonProperty("name")
    public String name() { return name; }
    @JsonProperty("type")
    public String type() { return type; }
    @JsonProperty("columnName")
    public String columnName() { return columnName; }
    @JsonProperty("displayName")
    public String displayName() { return displayName; }
    @JsonProperty("description")
    public String description() { return description; }
    @JsonProperty("required")
    public boolean required() { return required; }
    @JsonProperty("unique")
    public boolean unique() { return unique; }
    @JsonProperty("indexed")
    public boolean indexed() { return indexed; }
    @JsonProperty("searchable")
    public boolean searchable() { return searchable; }
    @JsonProperty("sortable")
    public boolean sortable() { return sortable; }
    @JsonProperty("filterable")
    public boolean filterable() { return filterable; }
    @JsonProperty("audited")
    public boolean audited() { return audited; }
    @JsonProperty("readOnly")
    public boolean readOnly() { return readOnly; }
    @JsonProperty("hidden")
    public boolean hidden() { return hidden; }
    @JsonProperty("defaultValue")
    public String defaultValue() { return defaultValue; }
    @JsonProperty("minLength")
    public Integer minLength() { return minLength; }
    @JsonProperty("maxLength")
    public Integer maxLength() { return maxLength; }
    @JsonProperty("min")
    public Long min() { return min; }
    @JsonProperty("max")
    public Long max() { return max; }
    @JsonProperty("pattern")
    public String pattern() { return pattern; }
    @JsonProperty("format")
    public String format() { return format; }
    @JsonProperty("enumType")
    public String enumType() { return enumType; }
    @JsonProperty("computed")
    public boolean computed() { return computed; }
    @JsonProperty("computedFrom")
    public List<String> computedFrom() { return computedFrom; }
    @JsonProperty("inCreate")
    public boolean inCreate() { return inCreate; }
    @JsonProperty("inUpdate")
    public boolean inUpdate() { return inUpdate; }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldMetadata that)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() { return Objects.hash(name); }

    @Override
    public String toString() {
        return "FieldMetadata{name='" + name + "', type='" + type + "', required=" + required + "}";
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
        private String enumType;
        private boolean computed = false;
        private List<String> computedFrom = List.of();
        private boolean inCreate = true;
        private boolean inUpdate = true;

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
        public Builder enumType(String v) { this.enumType = v; return this; }
        public Builder computed(boolean v) { this.computed = v; return this; }
        public Builder computedFrom(List<String> v) { this.computedFrom = v != null ? v : List.of(); return this; }
        public Builder inCreate(boolean v) { this.inCreate = v; return this; }
        public Builder inUpdate(boolean v) { this.inUpdate = v; return this; }

        public FieldMetadata build() {
            return new FieldMetadata(name, type, columnName, displayName, description, required, unique,
                    indexed, searchable, sortable, filterable, audited, readOnly, hidden, defaultValue,
                    minLength, maxLength, min, max, pattern, format, enumType, computed, computedFrom, inCreate, inUpdate);
        }
    }
}
