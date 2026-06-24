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
        List<String> producesEvents,
        /**
         * The simple name of the Java method the {@code @Action} annotates — distinct
         * from {@link #name()}, which is the action identity ({@code @Action(name=…)})
         * and may differ (e.g. renamed to avoid a bean-accessor collision). Carried so
         * build-time codegen can emit a server-side dispatch that invokes the actual
         * aggregate method. Optional: {@code null} when unknown (hand-built metadata or
         * legacy JSON); use {@link #effectiveMethodName()} for a name-based fallback.
         *
         * @since 0.7.0
         */
        String methodName,

        /**
         * Whether the action returns a streaming (server-push) response rather than
         * responding once — the AST twin of {@code @Action(streaming=true)}. When
         * {@code true}, build-time codegen emits a kernel {@code HttpStreamHandler}
         * bound to a streaming route (ADR-043) instead of a respond-once handler.
         *
         * @since 0.8.0
         */
        boolean streaming,

        /**
         * The SSE {@code event:} name carried on each emitted {@link #streaming()}
         * frame — the AST twin of {@code @Action(streamEventType=…)}. Optional:
         * {@code null} when unset (normalized from a blank annotation value).
         * Meaningful only when {@link #streaming()} is {@code true}.
         *
         * @since 0.8.0
         */
        String streamEventType,

        /**
         * Whether clients may subscribe to this action's progress in real time —
         * the AST twin of {@code @Action(realTimeUpdates=true)}. Distinct from
         * {@link #streaming()}: streaming is the response shape, this is the
         * subscribe-to-progress affordance.
         *
         * @since 0.8.0
         */
        boolean realTimeUpdates
) {

    public ActionMetadata {
        Objects.requireNonNull(name, "name is required");
        if (httpMethod == null) httpMethod = "POST";
        params = params != null ? List.copyOf(params) : List.of();
        permissions = permissions != null ? List.copyOf(permissions) : List.of();
        producesEvents = producesEvents != null ? List.copyOf(producesEvents) : List.of();
        if (streamEventType != null && streamEventType.isBlank()) streamEventType = null;
    }

    public static ActionMetadata simple(String name) {
        return new ActionMetadata(name, null, null, "POST", null, false, false, false, false, List.of(), List.of(), List.of(), null, false, null, false);
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
    public boolean hasStreamEventType() { return streamEventType != null; } // blank normalized to null in the compact constructor

    @JsonIgnore
    public String effectiveDisplayName() {
        return (displayName != null && !displayName.isBlank()) ? displayName : name;
    }

    /**
     * The Java method to dispatch to: {@link #methodName()} when known, else the
     * action {@link #name()} as a best-effort fallback (covers hand-built metadata
     * and legacy JSON written before {@code methodName} existed).
     */
    @JsonIgnore
    public String effectiveMethodName() {
        return (methodName != null && !methodName.isBlank()) ? methodName : name;
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
        private String methodName;
        private boolean streaming = false;
        private String streamEventType;
        private boolean realTimeUpdates = false;

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
        public Builder methodName(String v) { this.methodName = v; return this; }
        public Builder streaming(boolean v) { this.streaming = v; return this; }
        public Builder streamEventType(String v) { this.streamEventType = v; return this; }
        public Builder realTimeUpdates(boolean v) { this.realTimeUpdates = v; return this; }

        public ActionMetadata build() {
            return new ActionMetadata(name, displayName, description, httpMethod, resultType,
                    async, idempotent, dangerous, requiresConfirmation, params, permissions, producesEvents, methodName,
                    streaming, streamEventType, realTimeUpdates);
        }
    }
}
