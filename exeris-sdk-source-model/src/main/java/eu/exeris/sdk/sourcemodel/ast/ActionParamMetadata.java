package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Metadata for action parameters defined with @ActionParam annotation.
 *
 *
 * @param name the parameter's name, as the generated request surface exposes it
 * @param type the parameter's Java type, as written in source
 * @param displayName the label a generated UI shows for the input
 * @param description human-readable prose for generated documentation and help text
 * @param required whether the parameter must be supplied
 * @param defaultValue the value applied when a client supplies none, as source text
 * @param pattern the regular expression a string value must match
 * @param minLength the minimum string length; absent means no bound
 * @param maxLength the maximum string length; absent means no bound
 * @param min the inclusive numeric floor; absent means no bound
 * @param max the inclusive numeric ceiling; absent means no bound
 * @since 0.1
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

    /**
     * Compact constructor; applies this record's normalization rules.
     */
    public ActionParamMetadata {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(type, "type is required");
    }

    /**
     * Creates a required {@code ActionParamMetadata}.
     *
     * @param name the {@code name} the result carries
     * @param type the {@code type} the result carries
     * @return the {@code ActionParamMetadata}
     */
    public static ActionParamMetadata required(String name, String type) {
        return new ActionParamMetadata(name, type, null, null, true, null, null, null, null, null, null);
    }

    /**
     * Creates an optional {@code ActionParamMetadata}.
     *
     * @param name the {@code name} the result carries
     * @param type the {@code type} the result carries
     * @param defaultValue the {@code defaultValue} the result carries
     * @return the {@code ActionParamMetadata}
     */
    public static ActionParamMetadata optional(String name, String type, String defaultValue) {
        return new ActionParamMetadata(name, type, null, null, false, defaultValue, null, null, null, null, null);
    }

    /**
     * Starts a builder for a {@code ActionParamMetadata}.
     *
     * @param name the {@code name} the result carries
     * @param type the {@code type} the result carries
     * @return a new builder
     */
    public static Builder builder(String name, String type) {
        return new Builder(name, type);
    }

    /**
     * Whether a {@code pattern} is declared.
     *
     * @return {@code true} when {@link #pattern()} is present
     */
    @JsonIgnore
    public boolean hasValidation() {
        return pattern != null || minLength != null || maxLength != null || min != null || max != null;
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
     * A mutable builder for {@code ActionParamMetadata}.
     *
     * <p>Each setter sets the record component of the same name. Those components are
     * documented by the record's own {@code @param} tags and are deliberately not restated
     * here — a per-setter repetition of the component's meaning is filler, and filler is what
     * makes generated javadoc worth less than none.
     */
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

        /**
         * Builds the {@code ActionParamMetadata} from this builder's current state.
         *
         * @return the built {@code ActionParamMetadata}
         */
        public ActionParamMetadata build() {
            return new ActionParamMetadata(name, type, displayName, description, required,
                    defaultValue, pattern, minLength, maxLength, min, max);
        }
    }
}
