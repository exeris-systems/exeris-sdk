package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Metadata for action parameters defined with @ActionParam annotation.
 *
 * @author Exeris SDK Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ActionParamMetadata {

    private final String name;
    private final String type;
    private final String displayName;
    private final String description;
    private final boolean required;
    private final String defaultValue;
    private final String pattern;
    private final Integer minLength;
    private final Integer maxLength;
    private final Long min;
    private final Long max;

    @JsonCreator
    public ActionParamMetadata(
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("description") String description,
            @JsonProperty("required") boolean required,
            @JsonProperty("defaultValue") String defaultValue,
            @JsonProperty("pattern") String pattern,
            @JsonProperty("minLength") Integer minLength,
            @JsonProperty("maxLength") Integer maxLength,
            @JsonProperty("min") Long min,
            @JsonProperty("max") Long max) {
        this.name = Objects.requireNonNull(name, "name is required");
        this.type = Objects.requireNonNull(type, "type is required");
        this.displayName = displayName;
        this.description = description;
        this.required = required;
        this.defaultValue = defaultValue;
        this.pattern = pattern;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.min = min;
        this.max = max;
    }

    public static ActionParamMetadata required(String name, String type) {
        return new ActionParamMetadata(name, type, null, null, true, null, null, null, null, null, null);
    }

    public static ActionParamMetadata optional(String name, String type, String defaultValue) {
        return new ActionParamMetadata(name, type, null, null, false, defaultValue, null, null, null, null, null);
    }

    public static Builder builder(String name, String type) {
        return new Builder(name, type);
    }

    // Getters
    public String name() { return name; }
    public String type() { return type; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public boolean required() { return required; }
    public String defaultValue() { return defaultValue; }
    public String pattern() { return pattern; }
    public Integer minLength() { return minLength; }
    public Integer maxLength() { return maxLength; }
    public Long min() { return min; }
    public Long max() { return max; }

    public boolean hasValidation() {
        return pattern != null || minLength != null || maxLength != null || min != null || max != null;
    }

    public String effectiveDisplayName() {
        return (displayName != null && !displayName.isBlank()) ? displayName : name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionParamMetadata that)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() { return Objects.hash(name); }

    @Override
    public String toString() {
        return "ActionParamMetadata{name='" + name + "', type='" + type + "', required=" + required + "}";
    }

    public static final class Builder {
        private final String name;
        private final String type;
        private String displayName;
        private String description;
        private boolean required = false;
        private String defaultValue;
        private String pattern;
        private Integer minLength;
        private Integer maxLength;
        private Long min;
        private Long max;

        private Builder(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder required(boolean v) { this.required = v; return this; }
        public Builder defaultValue(String v) { this.defaultValue = v; return this; }
        public Builder pattern(String v) { this.pattern = v; return this; }
        public Builder minLength(Integer v) { this.minLength = v; return this; }
        public Builder maxLength(Integer v) { this.maxLength = v; return this; }
        public Builder min(Long v) { this.min = v; return this; }
        public Builder max(Long v) { this.max = v; return this; }

        public ActionParamMetadata build() {
            return new ActionParamMetadata(name, type, displayName, description, required,
                    defaultValue, pattern, minLength, maxLength, min, max);
        }
    }
}
