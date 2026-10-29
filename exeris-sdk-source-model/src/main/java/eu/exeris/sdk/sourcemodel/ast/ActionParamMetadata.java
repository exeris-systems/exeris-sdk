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
 * @param bindingSource where a generated handler reads the parameter from; absent means
 *        {@link ParamSource#BODY}, read through {@link #effectiveBindingSource()}
 * @param queryKey the query-string key a {@link ParamSource#QUERY} parameter is read under,
 *        when it differs from {@code name}; absent means the key is {@code name}, read through
 *        {@link #effectiveQueryKey()}. A blank key, or one equal to {@code name}, is stored as
 *        absent, so the two spellings of "the key is the name" are one on the wire
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
        Long max,
        ParamSource bindingSource,
        String queryKey
) {

    /**
     * Compact constructor; applies this record's normalization rules.
     *
     * <p>{@code queryKey} is stored as {@code null} when it is blank or equal to {@code name}:
     * a producer that writes the key only when it differs and one that always writes it then
     * build the same record.
     */
    public ActionParamMetadata {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(type, "type is required");
        if (queryKey != null && (queryKey.isBlank() || queryKey.equals(name))) {
            queryKey = null;
        }
    }

    /**
     * A delegating constructor for backward compatibility with code compiled against a previous
     * version of this record. Omits {@code bindingSource} and {@code queryKey}, both of which
     * default to {@code null}: a body parameter.
     *
     * <p>Prefer {@link #builder(String, String)}, which sets any component by name and does not
     * change shape when the record grows.
     *
     * @param name the {@code name} the result carries
     * @param type the {@code type} the result carries
     * @param displayName the {@code displayName} the result carries
     * @param description the {@code description} the result carries
     * @param required the {@code required} the result carries
     * @param defaultValue the {@code defaultValue} the result carries
     * @param pattern the {@code pattern} the result carries
     * @param minLength the {@code minLength} the result carries
     * @param maxLength the {@code maxLength} the result carries
     * @param min the {@code min} the result carries
     * @param max the {@code max} the result carries
     */
    public ActionParamMetadata(String name, String type, String displayName, String description,
                               boolean required, String defaultValue, String pattern,
                               Integer minLength, Integer maxLength, Long min, Long max) {
        this(name, type, displayName, description, required, defaultValue, pattern,
                minLength, maxLength, min, max, null, null);
    }

    /**
     * Creates a required {@code ActionParamMetadata}.
     *
     * @param name the {@code name} the result carries
     * @param type the {@code type} the result carries
     * @return the {@code ActionParamMetadata}
     */
    public static ActionParamMetadata required(String name, String type) {
        return new ActionParamMetadata(name, type, null, null, true, null, null, null, null, null, null, null, null);
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
        return new ActionParamMetadata(name, type, null, null, false, defaultValue, null, null, null, null, null, null, null);
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
     * The effective binding source: the declared one, or {@link ParamSource#BODY}.
     *
     * @return the source a generated handler reads this parameter from
     * @since 0.13
     */
    @JsonIgnore
    public ParamSource effectiveBindingSource() {
        return bindingSource != null ? bindingSource : ParamSource.BODY;
    }

    /**
     * The query-string key: the declared {@code queryKey}, or {@code name} when none is declared.
     * Meaningful only when {@link #effectiveBindingSource()} is {@link ParamSource#QUERY}.
     *
     * @return the key a generated handler reads this parameter under
     * @since 0.13
     */
    @JsonIgnore
    public String effectiveQueryKey() {
        return queryKey != null ? queryKey : name;
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
        private ParamSource bindingSource;
        private String queryKey;

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
        public Builder bindingSource(ParamSource v) { this.bindingSource = v; return this; }
        public Builder queryKey(String v) { this.queryKey = v; return this; }

        /**
         * Builds the {@code ActionParamMetadata} from this builder's current state.
         *
         * @return the built {@code ActionParamMetadata}
         */
        public ActionParamMetadata build() {
            return new ActionParamMetadata(name, type, displayName, description, required,
                    defaultValue, pattern, minLength, maxLength, min, max, bindingSource, queryKey);
        }
    }
}
