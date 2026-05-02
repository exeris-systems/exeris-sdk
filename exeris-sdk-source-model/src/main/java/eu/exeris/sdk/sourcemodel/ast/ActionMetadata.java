package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Metadata for domain actions defined with @Action annotation.
 *
 * @author Exeris Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ActionMetadata {

    private final String name;
    private final String displayName;
    private final String description;
    private final String httpMethod;
    private final String resultType;
    private final boolean async;
    private final boolean idempotent;
    private final boolean dangerous;
    private final boolean requiresConfirmation;
    private final List<ActionParamMetadata> params;
    private final List<String> permissions;
    private final List<String> producesEvents;

    @JsonCreator
    public ActionMetadata(
            @JsonProperty("name") String name,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("description") String description,
            @JsonProperty("httpMethod") String httpMethod,
            @JsonProperty("resultType") String resultType,
            @JsonProperty("async") boolean async,
            @JsonProperty("idempotent") boolean idempotent,
            @JsonProperty("dangerous") boolean dangerous,
            @JsonProperty("requiresConfirmation") boolean requiresConfirmation,
            @JsonProperty("params") List<ActionParamMetadata> params,
            @JsonProperty("permissions") List<String> permissions,
            @JsonProperty("producesEvents") List<String> producesEvents) {
        this.name = Objects.requireNonNull(name, "name is required");
        this.displayName = displayName;
        this.description = description;
        this.httpMethod = httpMethod != null ? httpMethod : "POST";
        this.resultType = resultType;
        this.async = async;
        this.idempotent = idempotent;
        this.dangerous = dangerous;
        this.requiresConfirmation = requiresConfirmation;
        this.params = params != null ? List.copyOf(params) : List.of();
        this.permissions = permissions != null ? List.copyOf(permissions) : List.of();
        this.producesEvents = producesEvents != null ? List.copyOf(producesEvents) : List.of();
    }

    public static ActionMetadata simple(String name) {
        return new ActionMetadata(name, null, null, "POST", null, false, false, false, false, List.of(), List.of(), List.of());
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    // Getters
    public String name() { return name; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public String httpMethod() { return httpMethod; }
    public String resultType() { return resultType; }
    public boolean async() { return async; }
    public boolean idempotent() { return idempotent; }
    public boolean dangerous() { return dangerous; }
    public boolean requiresConfirmation() { return requiresConfirmation; }
    public List<ActionParamMetadata> params() { return params; }
    public List<String> permissions() { return permissions; }
    public List<String> producesEvents() { return producesEvents; }

    public boolean hasParams() { return params != null && !params.isEmpty(); }
    public boolean hasPermissions() { return permissions != null && !permissions.isEmpty(); }
    public boolean hasProducedEvents() { return producesEvents != null && !producesEvents.isEmpty(); }

    public String effectiveDisplayName() {
        return (displayName != null && !displayName.isBlank()) ? displayName : name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionMetadata that)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() { return Objects.hash(name); }

    @Override
    public String toString() {
        return "ActionMetadata{name='" + name + "', httpMethod='" + httpMethod + "'}";
    }

    public static final class Builder {
        private final String name;
        private String displayName;
        private String description;
        private String httpMethod = "POST";
        private String resultType;
        private boolean async = false;
        private boolean idempotent = false;
        private boolean dangerous = false;
        private boolean requiresConfirmation = false;
        private List<ActionParamMetadata> params = new ArrayList<>();
        private List<String> permissions = new ArrayList<>();
        private List<String> producesEvents = new ArrayList<>();

        private Builder(String name) { this.name = name; }

        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder httpMethod(String v) { this.httpMethod = v; return this; }
        public Builder resultType(String v) { this.resultType = v; return this; }
        public Builder async(boolean v) { this.async = v; return this; }
        public Builder idempotent(boolean v) { this.idempotent = v; return this; }
        public Builder dangerous(boolean v) { this.dangerous = v; return this; }
        public Builder requiresConfirmation(boolean v) { this.requiresConfirmation = v; return this; }
        public Builder params(List<ActionParamMetadata> v) { this.params = new ArrayList<>(v); return this; }
        public Builder addParam(ActionParamMetadata p) { this.params.add(p); return this; }
        public Builder permissions(List<String> v) { this.permissions = new ArrayList<>(v); return this; }
        public Builder producesEvents(List<String> v) { this.producesEvents = new ArrayList<>(v); return this; }

        public ActionMetadata build() {
            return new ActionMetadata(name, displayName, description, httpMethod, resultType,
                    async, idempotent, dangerous, requiresConfirmation, params, permissions, producesEvents);
        }
    }
}
