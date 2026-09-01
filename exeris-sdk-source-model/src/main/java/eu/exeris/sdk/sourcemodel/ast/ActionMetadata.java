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
 * @param streaming Whether the action returns a streaming (server-push) response rather than
 *        responding once — the AST twin of {@code @Action(streaming=true)}. When
 *        {@code true}, build-time codegen emits a kernel {@code HttpStreamHandler}
 *        bound to a streaming route (ADR-043) instead of a respond-once handler.
 * @param streamEventType The SSE {@code event:} name carried on each emitted {@link #streaming()}
 *        frame — the AST twin of {@code @Action(streamEventType=…)}. Optional:
 *        {@code null} when unset (normalized from a blank annotation value).
 *        Meaningful only when {@link #streaming()} is {@code true}.
 * @param realTimeUpdates Whether clients may subscribe to this action's progress in real time —
 *        the AST twin of {@code @Action(realTimeUpdates=true)}. Distinct from
 *        {@link #streaming()}: streaming is the response shape, this is the
 *        subscribe-to-progress affordance.
 *
 *        <p><strong>Open-Core status — reserved, extraction pending
 *        tooling:</strong> unlike its two neighbours {@link #streaming()} and
 *        {@link #streamEventType()}, which the {@code exeris-tooling}
 *        processor does extract, this component is never populated from
 *        annotated source. The processor declines it by name — "deliberately
 *        NOT extracted here … extracting it would only create an inert
 *        {@code ActionMetadata} attribute" — the {@code NOTE:} comment in
 *        {@code ExerisDomainProcessor}'s {@code @Action} extraction
 *        ({@code exeris-tooling}, {@code exeris-processor/.../ExerisDomainProcessor.java};
 *        anchored on the comment rather than a line number, which has already
 *        moved once) —
 *        and no generator reads it back. On the build-time path it is
 *        therefore always {@code false}; only hand-built metadata can set it,
 *        and setting it changes no generated artifact. The extraction lands in
 *        the same change that introduces its consumer, matching the Open-Core
 *        status note on {@code @Action.realTimeUpdates()}.
 * @param schedule The schedule on which this action also fires without a client call —
 *        the AST twin of {@code @Schedule} on the action method. Optional:
 *        {@code null} when the action is call-only, which is the common case.
 *
 *        <p><strong>Open-Core status — reserved, extraction pending
 *        tooling:</strong> the kernel side exists ({@code JobScheduler} /
 *        {@code JobTrigger}, kernel ADR-057, shipped on the kernel 0.11 line
 *        with {@code AbstractJobSchedulerTck}), but no {@code exeris-tooling}
 *        processor extracts {@code @Schedule} and no generator submits a job
 *        from this component, so on the build-time path it is always
 *        {@code null}. The kernel holds {@code …spi.scheduling} at tier
 *        {@code preview}, so the component is excluded from the 1.0.0 freeze
 *        and a 1.x minor may still change it (ADR-072).
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
 *        {@code RouteRequirement}, kernel ADR-061, shipped on the kernel 0.11 line
 *        with {@code AbstractHttpRoutePolicyTck}), but no {@code exeris-tooling}
 *        processor extracts {@code @RouteAccess} and no generator emits a
 *        URL-to-policy table from this component, so on the build-time path it is
 *        always {@code null}. The kernel holds route authorization at tier
 *        {@code preview}, so the component is excluded from the 1.0.0 freeze and a
 *        1.x minor may still change it (ADR-072).
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
        String methodName,
        boolean streaming,
        String streamEventType,
        boolean realTimeUpdates,
        ScheduleMetadata schedule,
        RouteAccess routeAccess
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
        return new ActionMetadata(name, null, null, "POST", null, false, false, false, false, List.of(), List.of(), List.of(), null, false, null, false, null, null);
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
    public boolean isScheduled() { return schedule != null; }
    @JsonIgnore
    public boolean isPublicRoute() { return routeAccess == RouteAccess.PUBLIC; }

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
        public Builder params(List<ActionParamMetadata> v) { this.params = new ArrayList<>(v); return this; }
        public Builder addParam(ActionParamMetadata p) { this.params.add(p); return this; }
        public Builder permissions(List<String> v) { this.permissions = new ArrayList<>(v); return this; }
        public Builder producesEvents(List<String> v) { this.producesEvents = new ArrayList<>(v); return this; }
        public Builder methodName(String v) { this.methodName = v; return this; }
        public Builder streaming(boolean v) { this.streaming = v; return this; }
        public Builder streamEventType(String v) { this.streamEventType = v; return this; }
        public Builder realTimeUpdates(boolean v) { this.realTimeUpdates = v; return this; }
        public Builder schedule(ScheduleMetadata v) { this.schedule = v; return this; }
        public Builder routeAccess(RouteAccess v) { this.routeAccess = v; return this; }

        public ActionMetadata build() {
            return new ActionMetadata(name, displayName, description, httpMethod, resultType,
                    async, idempotent, dangerous, requiresConfirmation, params, permissions, producesEvents, methodName,
                    streaming, streamEventType, realTimeUpdates, schedule, routeAccess);
        }
    }
}
