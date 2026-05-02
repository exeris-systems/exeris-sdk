package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Metadata for domain actions defined with @Action annotation.
 *
 * @author Exeris SDK Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record ActionMetadata(
        String name,
        String displayName,
        String description,
        String httpMethod,
        String resultType,
        boolean async,
        boolean idempotent,
        boolean dangerous,
        boolean requiresConfirmation,
        List<ActionParamMetadata> params,
        List<String> permissions,
        List<String> producesEvents
) {

    public ActionMetadata {
        Objects.requireNonNull(name, "name is required");
        if (httpMethod == null) httpMethod = "POST";
        params = params != null ? List.copyOf(params) : List.of();
        permissions = permissions != null ? List.copyOf(permissions) : List.of();
        producesEvents = producesEvents != null ? List.copyOf(producesEvents) : List.of();
    }

    public static ActionMetadata simple(String name) {
        return new ActionMetadata(name, null, null, "POST", null, false, false, false, false, List.of(), List.of(), List.of());
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    @JsonIgnore
    public boolean hasParams() { return !params.isEmpty(); }
    @JsonIgnore
    public boolean hasPermissions() { return !permissions.isEmpty(); }
    @JsonIgnore
    public boolean hasProducedEvents() { return !producesEvents.isEmpty(); }

    @JsonIgnore
    public String effectiveDisplayName() {
        return (displayName != null && !displayName.isBlank()) ? displayName : name;
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
