package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Metadata for domain events.
 *
 * <p>The record originally carried only the bare 4-tuple
 * ({@code name} / {@code topic} / {@code description} / {@code aggregateType}):
 * it could name an event but not say <em>what its payload is</em>. The EV1
 * growth adds the resolved payload-field framing so the "publish this subset of
 * the entity's fields as the event payload" pattern is expressible:
 *
 * <ul>
 *   <li>{@code payloadFields} — the RESOLVED payload field <em>names</em>, in
 *       {@code @DomainEvent.includeFields} order when that attribute is set, else
 *       in entity-declaration order. Resolution semantics (shared by the processor
 *       and the {@code -io} reader, ADR-042 lock-step):
 *       ({@code @DomainEvent.includeFields} if non-empty, else ALL of the
 *       entity's {@code @Field} names) minus {@code @DomainEvent.excludeFields}.</li>
 *   <li>{@code sensitiveFields} — the {@code @DomainEvent.sensitiveFields} names
 *       to redact, verbatim.</li>
 * </ul>
 *
 * <p><strong>Lean, normalized.</strong> These are field <em>names</em>, not full
 * {@link FieldMetadata} copies — the entity's field definitions live once on
 * {@link DomainMetadata#fields()} and downstream tooling resolves a payload
 * field's type by name from there (the same zero-duplication discipline the
 * {@link ProjectionMetadata#fields()} subset uses). This follows the additive
 * by-name JSON / {@code @JsonInclude(NON_NULL)} grow precedent of
 * {@link DomainMetadata}'s {@code projections} / {@code eventHandlers} members.
 *
 * <p><strong>Wire form of the EV1 lists.</strong> The compact constructor
 * normalizes {@code null} to {@link List#of()}, so {@code payloadFields} and
 * {@code sensitiveFields} are never {@code null}; under the class-level
 * {@code @JsonInclude(NON_NULL)} an empty list therefore serializes as {@code []}
 * (it is not suppressed — {@code NON_NULL} only drops {@code null}). This matches
 * how {@link DomainMetadata}'s builder-constructed list members behave. Old tooling
 * stays safe via {@code @JsonIgnoreProperties(ignoreUnknown = true)}, and both an
 * absent key and {@code []} read back to {@link List#of()} through the compact
 * constructor, so the EV1 metadata can grow further without a wire break.
 *
 * <p><strong>EV2: the trigger triple.</strong> {@code trigger} / {@code actionName} /
 * {@code fieldName} say <em>when</em> the event fires. Until 0.11.0 the record carried no
 * trigger at all: the processor read {@code @DomainEvent.trigger} only to derive the event
 * <em>name</em> suffix ({@code CREATE} → {@code OrderCreatedEvent}) and then discarded it, so
 * a generator could not know where to place a publish call. Worse, the suffix is only applied
 * when the user did not supply an explicit {@code name}, so
 * {@code @DomainEvent(name = "OrderPlaced", trigger = CREATE)} left no trace of the trigger
 * anywhere. {@code action} and {@code field} — required by {@code ACTION} and
 * {@code FIELD_CHANGED} respectively — were not read at all.
 *
 * <p><strong>{@code trigger} is nullable, deliberately.</strong> {@code null} means "this
 * baseline predates EV2 extraction", which is a different claim from "fires on CREATE".
 * Defaulting it in the compact constructor would make the two indistinguishable and would
 * silently attach create-time publishing to every pre-0.11.0 event. Use {@link #hasTrigger()}
 * and treat absence as "emit no publish call". Note that this differs from
 * {@code DomainMetadata.effectiveDataScope()} (ADR-059), which <em>can</em> default because it
 * has a deprecated predecessor attribute to fall back through; there is no predecessor here.
 *
 * <p><strong>Out of EV1 scope.</strong> {@code @DomainEvent.includeComputed} and
 * {@code includePreviousValues} do not contribute to {@code payloadFields} yet —
 * there is no computed-field source in the persisted field list. See the
 * {@code // TODO(EV1)} note in {@code ExerisDomainProcessor.extractSingleEventMetadata}.
 *
 * @param name the event's name — its identity on the wire and in generated handlers
 * @param topic the messaging topic the event is published to
 * @param description human-readable prose for generated documentation
 * @param aggregateType the aggregate the event originates from
 * @param payloadFields the entity fields carried in the event payload
 * @param sensitiveFields the payload fields holding sensitive data, which generated
 *        serialization and logging must treat accordingly
 *
 * @param trigger when the event fires — the discriminator between a lifecycle moment, an
 *        action, and a field change
 *
 * @param actionName the action the event fires on, when {@link #trigger()} names one
 * @param fieldName the field whose change fires the event, when {@link #trigger()} names one
 * @since 0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DomainEventMetadata(
        @JsonProperty("name") String name,
        @JsonProperty("topic") String topic,
        @JsonProperty("description") String description,
        @JsonProperty("aggregateType") String aggregateType,
        // ── EV1: resolved payload framing (field names, not FieldMetadata copies) ──
        @JsonProperty("payloadFields") List<String> payloadFields,
        @JsonProperty("sensitiveFields") List<String> sensitiveFields,
        // ── EV2: WHEN the event fires. Nullable — see the class javadoc. ──
        @JsonProperty("trigger") Trigger trigger,
        @JsonProperty("actionName") String actionName,
        @JsonProperty("fieldName") String fieldName
) {

    /**
     * When a domain event fires. AST-owned and independent of
     * {@code @DomainEvent.Trigger}, bridged by constant-name identity at extraction —
     * the {@code SagaStepMetadata.StepKind} / {@code DataScope} precedent (ADR-059),
     * which keeps the annotations module free of a source-model dependency.
     *
     * @since 0.11
     */
    public enum Trigger {
        /** Published after the aggregate is created. */
        CREATE,
        /** Published after the aggregate is updated. */
        UPDATE,
        /** Published after the aggregate is deleted. */
        DELETE,
        /** Published when a named {@code @Action} runs; see {@link #actionName()}. */
        ACTION,
        /** Published when a named field changes; see {@link #fieldName()}. */
        FIELD_CHANGED,
        /** Published on a state-machine transition. */
        STATE_TRANSITION,
        /** Published by a scheduler. */
        SCHEDULED,
        /** Published by an explicit application call. */
        MANUAL,
        /** Published on aggregate snapshot. */
        SNAPSHOT
    }

    /**
     * Normalizes null lists to empty lists (stable wire form whether built via
     * the builder, a convenience factory, or constructed directly) and takes
     * defensive copies (deterministic, immutable carriers).
     */
    public DomainEventMetadata {
        payloadFields = (payloadFields == null) ? List.of() : List.copyOf(payloadFields);
        sensitiveFields = (sensitiveFields == null) ? List.of() : List.copyOf(sensitiveFields);
        // trigger is deliberately NOT defaulted here. See the class javadoc: a null
        // trigger means "this baseline predates trigger extraction", which is not the
        // same claim as "fires on CREATE", and a generator must be able to tell them
        // apart before it emits a publish call.
    }

    /**
     * Pre-EV1 4-tuple constructor — keeps existing call sites compiling and
     * defaults both EV1 payload lists to empty. The grown 6-arg canonical
     * constructor (or {@link #builder(String)}) carries the resolved payload.
     *
     * @param name the {@code name} the result carries
     * @param topic the {@code topic} the result carries
     * @param description the {@code description} the result carries
     * @param aggregateType the {@code aggregateType} the result carries
     */
    public DomainEventMetadata(String name, String topic, String description, String aggregateType) {
        this(name, topic, description, aggregateType, List.of(), List.of(), null, null, null);
    }

    /**
     * Pre-EV2 6-arg constructor — keeps the EV1 call sites compiling and leaves the
     * trigger triple unset (i.e. "not extracted", per the class javadoc).
     *
     * @param name the {@code name} the result carries
     * @param topic the {@code topic} the result carries
     * @param description the {@code description} the result carries
     * @param aggregateType the {@code aggregateType} the result carries
     * @param payloadFields the {@code payloadFields} the result carries
     * @param sensitiveFields the {@code sensitiveFields} the result carries
     * @since 0.11
     */
    public DomainEventMetadata(String name, String topic, String description, String aggregateType,
                               List<String> payloadFields, List<String> sensitiveFields) {
        this(name, topic, description, aggregateType, payloadFields, sensitiveFields,
                null, null, null);
    }

    /**
     * Creates a minimal {@code DomainEventMetadata}, with only the essentials set.
     *
     * @param name the {@code name} the result carries
     * @return the {@code DomainEventMetadata}
     */
    public static DomainEventMetadata simple(String name) {
        return new DomainEventMetadata(name, null, null, null, List.of(), List.of(),
                null, null, null);
    }

    /**
     * Creates a {@code DomainEventMetadata}.
     *
     * @param name the {@code name} the result carries
     * @param topic the {@code topic} the result carries
     * @return the {@code DomainEventMetadata}
     */
    public static DomainEventMetadata withTopic(String name, String topic) {
        return new DomainEventMetadata(name, topic, null, null, List.of(), List.of(),
                null, null, null);
    }

    /**
     * Starts a builder for a {@code DomainEventMetadata}.
     *
     * @param name the event's name
     * @return a new builder
     * @since 0.8 (EV1)
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * True when the event declares a resolved payload field subset.
     *
     * @return the {@code boolean}
     */
    public boolean hasPayloadFields() {
        return !payloadFields.isEmpty();
    }

    /**
     * True when the event declares sensitive fields to redact.
     *
     * @return the {@code boolean}
     */
    public boolean hasSensitiveFields() {
        return !sensitiveFields.isEmpty();
    }

    /**
     * True when this event says <em>when</em> it fires. False means the baseline predates
     * EV2 trigger extraction — <b>not</b> that the event fires on create.
     *
     * @return the {@code boolean}
     * @since 0.11
     */
    public boolean hasTrigger() {
        return trigger != null;
    }

    /** Mirrors the relevant {@code @DomainEvent} attribute defaults (empty lists). */
    public static final class Builder {
        private final String name;
        private String topic;
        private String description;
        private String aggregateType;
        private List<String> payloadFields = List.of();
        private List<String> sensitiveFields = List.of();
        private Trigger trigger;
        private String actionName;
        private String fieldName;

        private Builder(String name) {
            this.name = name;
        }

        public Builder topic(String v) { this.topic = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder aggregateType(String v) { this.aggregateType = v; return this; }
        public Builder payloadFields(List<String> v) { this.payloadFields = v; return this; }
        public Builder sensitiveFields(List<String> v) { this.sensitiveFields = v; return this; }
        /** One of the moments an event can be declared to fire at. @since 0.11. */
        public Builder trigger(Trigger v) { this.trigger = v; return this; }
        /** One of the moments an event can be declared to fire at. @since 0.11. */
        public Builder actionName(String v) { this.actionName = v; return this; }
        /** One of the moments an event can be declared to fire at. @since 0.11. */
        public Builder fieldName(String v) { this.fieldName = v; return this; }

        /**
         * Builds the {@code DomainEventMetadata} from this builder's current state.
         *
         * @return the built {@code DomainEventMetadata}
         */
        public DomainEventMetadata build() {
            return new DomainEventMetadata(
                    name, topic, description, aggregateType, payloadFields, sensitiveFields,
                    trigger, actionName, fieldName);
        }
    }
}
