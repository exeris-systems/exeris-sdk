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
 * @param methodName The simple name of the Java method the {@code @Action} annotates — distinct
 *        from {@link #name()}, which is the action identity ({@code @Action(name=…)})
 *        and may differ (e.g. renamed to avoid a bean-accessor collision). Carried so
 *        build-time codegen can emit a server-side dispatch that invokes the actual
 *        aggregate method. Optional: {@code null} when unknown (hand-built metadata or
 *        legacy JSON); use {@link #effectiveMethodName()} for a name-based fallback.
 *
 * @param streaming Whether the action returns a streaming (server-push) response rather than
 *        responding once — the AST twin of {@code @Action(streaming=true)}. When
 *        {@code true}, build-time codegen emits a kernel {@code HttpStreamHandler}
 *        bound to a streaming route (ADR-043) instead of a respond-once handler.
 *
 * @param streamEventType The SSE {@code event:} name carried on each emitted {@link #streaming()}
 *        frame — the AST twin of {@code @Action(streamEventType=…)}. Optional:
 *        {@code null} when unset (normalized from a blank annotation value).
 *        Meaningful only when {@link #streaming()} is {@code true}.
 *
 * @param realTimeUpdates Whether clients may subscribe to this action's progress in real time —
 *        the AST twin of {@code @Action(realTimeUpdates=true)}. Distinct from
 *        {@link #streaming()}: streaming is the response shape, this is the
 *        subscribe-to-progress affordance.
 *
 *        <p><strong>Open-Core status — reserved, extraction pending
 *        tooling:</strong> this component is never extracted from annotated source
 *        and is always {@code false} on the build-time path. Only hand-built metadata
 *        can set it, and setting it changes no generated artifact.
 *
 * @param schedule The schedule on which this action also fires without a client call —
 *        the AST twin of {@code @Schedule} on the action method. Optional:
 *        {@code null} when the action is call-only, which is the common case.
 *
 *        <p><strong>Open-Core status — reserved, extraction pending
 *        tooling:</strong> the kernel side exists ({@code JobScheduler} /
 *        {@code JobTrigger}, kernel ADR-057), but no {@code exeris-tooling}
 *        processor extracts {@code @Schedule} and no generator submits a job
 *        from this component, so on the build-time path it is always
 *        {@code null}. The kernel holds {@code …spi.scheduling} at tier
 *        {@code preview}, so the component is excluded from the 1.0.0 freeze
 *        and a 1.x minor may still change it (ADR-072).
 *
 * @param routeAccess What this action's generated route demands of its caller — the identity
 *        half of the kernel's route-authorization decision (kernel ADR-061), and
 *        the AST twin of {@code @RouteAccess} on the action method.
 *
 *        <p>{@code null} means the author declared nothing, and the generated
 *        policy's default decides; there is deliberately no {@code UNSPECIFIED}
 *        constant (see {@link RouteAccess}). A value here overrides the
 *        entity-level {@link DomainMetadata#routeAccess()} for this action alone —
 *        nearest declaration wins.
 *
 *        <p><strong>Open-Core status — reserved, extraction pending
 *        tooling:</strong> the kernel side exists ({@code HttpRoutePolicy} /
 *        {@code RouteRequirement}, kernel ADR-061), but no {@code exeris-tooling}
 *        processor extracts {@code @RouteAccess} and no generator emits a
 *        URL-to-policy table from this component, so on the build-time path it is
 *        always {@code null}. The kernel holds route authorization at tier
 *        {@code preview}, so the component is excluded from the 1.0.0 freeze and a
 *        1.x minor may still change it (ADR-072).
 *
 * @param name the action's identity, as the generated surface exposes it — distinct from
 *        {@link #methodName()}, the Java method behind it
 *
 * @param displayName the label a generated UI shows for the action
 * @param description human-readable prose for generated documentation
 * @param httpMethod the HTTP method the generated route is bound to
 * @param resultType the action's return type, as written in source
 * @param async whether the action returns before its work completes
 * @param idempotent whether repeating the call is safe
 * @param dangerous whether the action is destructive enough to warrant a warning
 * @param requiresConfirmation whether a generated UI asks before invoking it
 * @param params the action's parameters, in declaration order
 * @param permissions the permissions required to invoke the action
 * @param producesEvents the events the action publishes on success
 * @since 0.1
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
        String methodName,
        boolean streaming,
        String streamEventType,
        boolean realTimeUpdates,
        ScheduleMetadata schedule,
        RouteAccess routeAccess
) {

    /**
     * Compact constructor; applies this record's normalization rules.
     */
    public ActionMetadata {
        Objects.requireNonNull(name, "name is required");
        if (httpMethod == null) httpMethod = "POST";
        params = AstLists.copyOfNoNulls(params, "params");
        permissions = AstLists.copyOfNoNulls(permissions, "permissions");
        producesEvents = AstLists.copyOfNoNulls(producesEvents, "producesEvents");
        if (streamEventType != null && streamEventType.isBlank()) streamEventType = null;
    }

    /**
     * A delegating constructor for backward compatibility with code compiled against a previous
     * version of this record. This constructor omits {@code routeAccess}, which defaults to {@code null}:
     * the author declared nothing, and the generated policy's default decides. The compact
     * constructor's normalization applies as it does to every call.
     *
     * <p>Prefer {@link #builder(String)}, which sets any component by name and does not change
     * shape when the record grows.
     *
     * @param name the {@code name} the result carries
     * @param displayName the {@code displayName} the result carries
     * @param description the {@code description} the result carries
     * @param httpMethod the {@code httpMethod} the result carries
     * @param resultType the {@code resultType} the result carries
     * @param async the {@code async} the result carries
     * @param idempotent the {@code idempotent} the result carries
     * @param dangerous the {@code dangerous} the result carries
     * @param requiresConfirmation the {@code requiresConfirmation} the result carries
     * @param params the {@code params} the result carries
     * @param permissions the {@code permissions} the result carries
     * @param producesEvents the {@code producesEvents} the result carries
     * @param methodName the {@code methodName} the result carries
     * @param streaming the {@code streaming} the result carries
     * @param streamEventType the {@code streamEventType} the result carries
     * @param realTimeUpdates the {@code realTimeUpdates} the result carries
     * @param schedule the {@code schedule} the result carries
     */
    public ActionMetadata(String name, String displayName, String description, String httpMethod,
                          String resultType, boolean async, boolean idempotent, boolean dangerous,
                          boolean requiresConfirmation, List<ActionParamMetadata> params,
                          List<String> permissions, List<String> producesEvents, String methodName,
                          boolean streaming, String streamEventType, boolean realTimeUpdates,
                          ScheduleMetadata schedule) {
        this(name, displayName, description, httpMethod, resultType, async, idempotent, dangerous,
                requiresConfirmation, params, permissions, producesEvents, methodName, streaming,
                streamEventType, realTimeUpdates, schedule, null);
    }

    /**
     * Creates a minimal {@code ActionMetadata}, with only the essentials set.
     *
     * @param name the {@code name} the result carries
     * @return the {@code ActionMetadata}
     */
    public static ActionMetadata simple(String name) {
        return new ActionMetadata(name, null, null, "POST", null, false, false, false, false, List.of(), List.of(), List.of(), null, false, null, false, null, null);
    }

    /**
     * Starts a builder for a {@code ActionMetadata}.
     *
     * @param name the {@code name} the result carries
     * @return a new builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Whether any {@code params} is declared.
     *
     * @return {@code true} when {@link #params()} is neither null nor empty
     */
    @JsonIgnore
    public boolean hasParams() { return !params.isEmpty(); }
    /**
     * Whether any {@code permissions} is declared.
     *
     * @return {@code true} when {@link #permissions()} is neither null nor empty
     */
    @JsonIgnore
    public boolean hasPermissions() { return !permissions.isEmpty(); }
    /**
     * Whether any {@code producesEvents} is declared.
     *
     * @return {@code true} when {@link #producesEvents()} is neither null nor empty
     */
    @JsonIgnore
    public boolean hasProducedEvents() { return !producesEvents.isEmpty(); }
    /**
     * Whether a {@code streamEventType} is declared.
     *
     * @return {@code true} when {@link #streamEventType()} is present
     */
    @JsonIgnore
    public boolean hasStreamEventType() { return streamEventType != null; } // blank normalized to null in the compact constructor
    /**
     * Whether a {@code schedule} is declared.
     *
     * @return {@code true} when {@link #schedule()} is present
     */
    @JsonIgnore
    public boolean isScheduled() { return schedule != null; }
    /**
     * Whether this action's route was explicitly declared public.
     *
     * @return {@code isPublicRoute} as this record reports it
     */
    @JsonIgnore
    public boolean isPublicRoute() { return routeAccess == RouteAccess.PUBLIC; }

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
     * The Java method to dispatch to: {@link #methodName()} when known, else the
     * action {@link #name()} as a best-effort fallback (covers hand-built metadata
     * and legacy JSON written before {@code methodName} existed).
     *
     * @return the {@code String}
     */
    @JsonIgnore
    public String effectiveMethodName() {
        return (methodName != null && !methodName.isBlank()) ? methodName : name;
    }

    /**
     * A mutable builder for {@code ActionMetadata}.
     *
     * <p>Each setter sets the record component of the same name. Those components are
     * documented by the record's own {@code @param} tags and are deliberately not restated
     * here — a per-setter repetition of the component's meaning is filler, and filler is what
     * makes generated javadoc worth less than none.
     */
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
        private ScheduleMetadata schedule;
        private RouteAccess routeAccess;

        private Builder(String name) { this.name = name; }

        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder httpMethod(String v) { this.httpMethod = v; return this; }
        public Builder resultType(String v) { this.resultType = v; return this; }
        public Builder async(boolean v) { this.async = v; return this; }
        public Builder idempotent(boolean v) { this.idempotent = v; return this; }
        public Builder dangerous(boolean v) { this.dangerous = v; return this; }
        public Builder requiresConfirmation(boolean v) { this.requiresConfirmation = v; return this; }
        /**
         * Replaces the parameters with a copy of the given list.
         *
         * <p>A copy, not the list itself, because {@link #addParam} appends in place afterwards — storing
         * the argument would make that append mutate the caller's list.
         *
         * @param v the parameters
         * @return this builder
         * @throws NullPointerException if {@code v} is {@code null}; the record's own constructor treats a
         *         null list as an empty one, and this setter does not
         */
        public Builder params(List<ActionParamMetadata> v) { this.params = new ArrayList<>(v); return this; }
        /**
         * Appends one parameter, keeping any already set.
         *
         * <p>The only setter here that adds rather than replaces. It works before {@link #params} is
         * called, the builder starting from an empty list.
         *
         * @param p the parameter to append
         * @return this builder
         */
        public Builder addParam(ActionParamMetadata p) { this.params.add(p); return this; }
        /**
         * Replaces the permissions with a copy of the given list.
         *
         * @param v the permissions
         * @return this builder
         * @throws NullPointerException if {@code v} is {@code null}; the record's own constructor treats a
         *         null list as an empty one, and this setter does not
         */
        public Builder permissions(List<String> v) { this.permissions = new ArrayList<>(v); return this; }
        /**
         * Replaces the produced events with a copy of the given list.
         *
         * @param v the produced events
         * @return this builder
         * @throws NullPointerException if {@code v} is {@code null}; the record's own constructor treats a
         *         null list as an empty one, and this setter does not
         */
        public Builder producesEvents(List<String> v) { this.producesEvents = new ArrayList<>(v); return this; }
        public Builder methodName(String v) { this.methodName = v; return this; }
        public Builder streaming(boolean v) { this.streaming = v; return this; }
        public Builder streamEventType(String v) { this.streamEventType = v; return this; }
        public Builder realTimeUpdates(boolean v) { this.realTimeUpdates = v; return this; }
        public Builder schedule(ScheduleMetadata v) { this.schedule = v; return this; }
        public Builder routeAccess(RouteAccess v) { this.routeAccess = v; return this; }

        /**
         * Builds the {@code ActionMetadata} from this builder's current state.
         *
         * @return the built {@code ActionMetadata}
         */
        public ActionMetadata build() {
            return new ActionMetadata(name, displayName, description, httpMethod, resultType,
                    async, idempotent, dangerous, requiresConfirmation, params, permissions, producesEvents, methodName,
                    streaming, streamEventType, realTimeUpdates, schedule, routeAccess);
        }
    }
}
