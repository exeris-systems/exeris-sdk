package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Metadata for action parameters defined with @ActionParam annotation.
 *
 * @author Exeris SDK Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActionParamMetadata(
        String name,
        String type,
        String displayName,
        String description,
        boolean required,
        String defaultValue,
        String pattern,
        Integer minLength,
        Integer maxLength,
        Long min,
        Long max
) {

    public ActionParamMetadata {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(type, "type is required");
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

    @JsonIgnore
    public boolean hasValidation() {
        return pattern != null || minLength != null || maxLength != null || min != null || max != null;
    }

    @JsonIgnore
    public String effectiveDisplayName() {
        return (displayName != null && !displayName.isBlank()) ? displayName : name;
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
