package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Metadata for internal API visibility and access control.
 * Supports read-only mode, disabled actions, and internal-only access.
 *
 * @param hidden whether the entity is omitted from generated public documentation
 * @param readOnly whether the generated surface offers reads only
 * @param internal whether the entity is internal — the predicate
 *        {@link DomainMetadata#isInternal()} reads
 * @param reason why the surface is restricted, for generated documentation
 * @param since the version from which the restriction applies
 * @param disabledActions the actions withheld from the generated surface
 * @param allowedRoles the roles still permitted to reach it
 * @author Exeris SDK Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InternalApiMetadata(
        boolean hidden,
        boolean readOnly,
        boolean internal,
        String reason,
        String since,
        List<String> disabledActions,
        List<String> allowedRoles
) {
    public static InternalApiMetadata hidden(String reason) {
        return new InternalApiMetadata(true, false, false, reason, null, List.of(), List.of());
    }

    public static InternalApiMetadata readOnly(String reason) {
        return new InternalApiMetadata(false, true, false, reason, null, List.of("create", "update", "delete"), List.of());
    }

    public static InternalApiMetadata internal(String reason) {
        return new InternalApiMetadata(false, false, true, reason, null, List.of(), List.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isActionDisabled(String actionName) {
        return disabledActions != null && disabledActions.contains(actionName);
    }

    public static final class Builder {
        private boolean hidden = false;
        private boolean readOnly = false;
        private boolean internal = false;
        private String reason;
        private String since;
        private List<String> disabledActions = List.of();
        private List<String> allowedRoles = List.of();

        public Builder hidden(boolean v) { this.hidden = v; return this; }
        public Builder readOnly(boolean v) { this.readOnly = v; return this; }
        public Builder internal(boolean v) { this.internal = v; return this; }
        public Builder reason(String v) { this.reason = v; return this; }
        public Builder since(String v) { this.since = v; return this; }
        public Builder disabledActions(List<String> v) { this.disabledActions = v; return this; }
        public Builder allowedRoles(List<String> v) { this.allowedRoles = v; return this; }

        public InternalApiMetadata build() {
            return new InternalApiMetadata(hidden, readOnly, internal, reason, since, disabledActions, allowedRoles);
        }
    }
}

