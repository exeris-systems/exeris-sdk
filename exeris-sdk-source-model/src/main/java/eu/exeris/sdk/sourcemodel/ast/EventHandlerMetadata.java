package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Metadata for an {@code @EventHandler} method — the <em>reaction</em> side of
 * the event surface, paired with the <em>emission</em> side captured by
 * {@link DomainEventMetadata}.
 *
 * <p>The {@code @EventHandler} annotation has shipped since 0.1.0, but until now
 * there was no AST record to extract it into: the processor could read the
 * annotation and had nowhere to put it, so the choreography backbone ("when
 * event X fires, do Y") was invisible to the AST and to codegen. This record
 * closes that gap (the SDK half — the build-time extraction + codegen consumer
 * are {@code exeris-tooling} work, tracked under the 0.6.0–0.9.0 AST-expressiveness
 * roadmap items).
 *
 * <p><strong>Scope.</strong> This first cut models the behaviourally meaningful
 * facets — identity, event selection, ordering, execution semantics, and the
 * saga-trigger surface. The annotation's purely operational attributes
 * ({@code emitMetrics} / {@code logLevel} / {@code alertOnFailure}), the nested
 * {@code @RetryPolicy}, and {@code versions} are deliberately left out for now;
 * they are additive and can grow onto this record later without a wire break
 * (by-name JSON, {@code @JsonInclude(NON_NULL)}).
 *
 * <p>Enum-valued attributes ({@code priority}, {@code transactionMode}) are held
 * as their source-written {@code String} form, not as duplicated AST enums — the
 * same discipline the capability records use for service/version, so the
 * dependency-light {@code source-model} module never pulls in the annotation
 * module's enum types.
 *
 * @param name the handler's identity
 * @param description human-readable prose for generated documentation
 * @param event the single event the handler consumes, when it consumes exactly one
 * @param events the events the handler consumes, when it consumes several
 * @param eventClassNames the fully qualified types of those events
 * @param topic the messaging topic the handler subscribes to
 * @param condition an expression gating whether a received event is handled
 * @param order the handler's position among handlers of the same event
 * @param priority the dispatch priority relative to other handlers
 * @param idempotent whether re-delivering the same event is safe
 * @param async whether the handler runs off the publishing thread
 * @param timeout how long the handler may run, as an ISO-8601 duration
 * @param transactionMode how the handler participates in a transaction
 * @param triggerStep the saga step this handler advances, when it drives one
 * @param expectedEvents the events that let the saga proceed
 * @param failureEvents the events that put the saga into compensation
 * @since 0.6
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventHandlerMetadata(
        // ── identity ──────────────────────────────────────────────────────────
        String name,
        String description,
        // ── event selection ──────────────────────────────────────────────────
        String event,
        List<String> events,
        List<String> eventClassNames,
        String topic,
        String condition,
        // ── ordering ──────────────────────────────────────────────────────────
        int order,
        String priority,
        // ── execution ─────────────────────────────────────────────────────────
        boolean idempotent,
        boolean async,
        String timeout,
        String transactionMode,
        // ── saga-specific ─────────────────────────────────────────────────────
        String triggerStep,
        List<String> expectedEvents,
        List<String> failureEvents
) {

    /**
     * Normalizes blank strings to {@code null} (so {@code @JsonInclude(NON_NULL)}
     * drops an annotation's empty-string default rather than serializing
     * {@code ""}) and null lists to empty lists (so direct construction matches
     * the builder default and the wire form is stable).
     */
    public EventHandlerMetadata {
        name = blankToNull(name);
        description = blankToNull(description);
        event = blankToNull(event);
        topic = blankToNull(topic);
        condition = blankToNull(condition);
        priority = blankToNull(priority);
        timeout = blankToNull(timeout);
        transactionMode = blankToNull(transactionMode);
        triggerStep = blankToNull(triggerStep);
        events = (events == null) ? List.of() : List.copyOf(events);
        eventClassNames = (eventClassNames == null) ? List.of() : List.copyOf(eventClassNames);
        expectedEvents = (expectedEvents == null) ? List.of() : List.copyOf(expectedEvents);
        failureEvents = (failureEvents == null) ? List.of() : List.copyOf(failureEvents);
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    // ── convenience ─────────────────────────────────────────────────────────

    /**
     * True when this handler participates in a saga (drives or awaits a step).
     *
     * @return the {@code boolean}
     */
    public boolean isSagaHandler() {
        return triggerStep != null || !expectedEvents.isEmpty() || !failureEvents.isEmpty();
    }

    /**
     * True when a SpEL filter narrows which events this handler reacts to.
     *
     * @return the {@code boolean}
     */
    public boolean hasCondition() {
        return condition != null;
    }

    /**
     * A minimal handler reacting to a single named event with the annotation's
     * default ordering / execution semantics.
     *
     * @param name the {@code name} the result carries
     * @param event the {@code event} the result carries
     * @return the {@code EventHandlerMetadata}
     */
    public static EventHandlerMetadata simple(String name, String event) {
        return builder(name).event(event).build();
    }

    /**
     * Starts a builder for a {@code EventHandlerMetadata}.
     *
     * @param name the {@code name} the result carries
     * @return a new builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** Mirrors the {@code @EventHandler} attribute defaults. */
    public static final class Builder {
        private final String name;
        private String description = "";
        private String event = "";
        private List<String> events = List.of();
        private List<String> eventClassNames = List.of();
        private String topic = "";
        private String condition = "";
        private int order = 100;
        private String priority = "NORMAL";
        private boolean idempotent = true;
        private boolean async = false;
        private String timeout = "PT30S";
        private String transactionMode = "REQUIRED";
        private String triggerStep = "";
        private List<String> expectedEvents = List.of();
        private List<String> failureEvents = List.of();

        private Builder(String name) {
            this.name = name;
        }

        public Builder description(String v) { this.description = v; return this; }
        public Builder event(String v) { this.event = v; return this; }
        public Builder events(List<String> v) { this.events = v; return this; }
        public Builder eventClassNames(List<String> v) { this.eventClassNames = v; return this; }
        public Builder topic(String v) { this.topic = v; return this; }
        public Builder condition(String v) { this.condition = v; return this; }
        public Builder order(int v) { this.order = v; return this; }
        public Builder priority(String v) { this.priority = v; return this; }
        public Builder idempotent(boolean v) { this.idempotent = v; return this; }
        public Builder async(boolean v) { this.async = v; return this; }
        public Builder timeout(String v) { this.timeout = v; return this; }
        public Builder transactionMode(String v) { this.transactionMode = v; return this; }
        public Builder triggerStep(String v) { this.triggerStep = v; return this; }
        public Builder expectedEvents(List<String> v) { this.expectedEvents = v; return this; }
        public Builder failureEvents(List<String> v) { this.failureEvents = v; return this; }

        /**
         * Builds the {@code EventHandlerMetadata} from this builder's current state.
         *
         * @return the built {@code EventHandlerMetadata}
         */
        public EventHandlerMetadata build() {
            return new EventHandlerMetadata(
                    name, description, event, events, eventClassNames, topic, condition,
                    order, priority, idempotent, async, timeout, transactionMode,
                    triggerStep, expectedEvents, failureEvents);
        }
    }
}
