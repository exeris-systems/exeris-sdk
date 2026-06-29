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
 *       entity-declaration order. Resolution semantics (shared by the processor
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
 * {@link DomainMetadata}'s {@code projections} / {@code eventHandlers} members:
 * absent lists are simply missing on the wire, and the EV1 metadata can grow
 * further without a wire break.
 *
 * <p><strong>Out of EV1 scope.</strong> {@code @DomainEvent.includeComputed} and
 * {@code includePreviousValues} do not contribute to {@code payloadFields} yet —
 * there is no computed-field source in the persisted field list. See the
 * {@code // TODO(EV1)} note in {@code ExerisDomainProcessor.extractSingleEventMetadata}.
 *
 * @author Exeris SDK Team
 * @since 0.1.0
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
        @JsonProperty("sensitiveFields") List<String> sensitiveFields
) {

    /**
     * Normalizes null lists to empty lists (stable wire form whether built via
     * the builder, a convenience factory, or constructed directly) and takes
     * defensive copies (deterministic, immutable carriers).
     */
    public DomainEventMetadata {
        payloadFields = (payloadFields == null) ? List.of() : List.copyOf(payloadFields);
        sensitiveFields = (sensitiveFields == null) ? List.of() : List.copyOf(sensitiveFields);
    }

    /**
     * Pre-EV1 4-tuple constructor — keeps existing call sites compiling and
     * defaults both EV1 payload lists to empty. The grown 6-arg canonical
     * constructor (or {@link #builder(String)}) carries the resolved payload.
     */
    public DomainEventMetadata(String name, String topic, String description, String aggregateType) {
        this(name, topic, description, aggregateType, List.of(), List.of());
    }

    public static DomainEventMetadata simple(String name) {
        return new DomainEventMetadata(name, null, null, null, List.of(), List.of());
    }

    public static DomainEventMetadata withTopic(String name, String topic) {
        return new DomainEventMetadata(name, topic, null, null, List.of(), List.of());
    }

    /** @since 0.1.0 (EV1) */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** True when the event declares a resolved payload field subset. */
    public boolean hasPayloadFields() {
        return !payloadFields.isEmpty();
    }

    /** True when the event declares sensitive fields to redact. */
    public boolean hasSensitiveFields() {
        return !sensitiveFields.isEmpty();
    }

    /** Mirrors the relevant {@code @DomainEvent} attribute defaults (empty lists). */
    public static final class Builder {
        private final String name;
        private String topic;
        private String description;
        private String aggregateType;
        private List<String> payloadFields = List.of();
        private List<String> sensitiveFields = List.of();

        private Builder(String name) {
            this.name = name;
        }

        public Builder topic(String v) { this.topic = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder aggregateType(String v) { this.aggregateType = v; return this; }
        public Builder payloadFields(List<String> v) { this.payloadFields = v; return this; }
        public Builder sensitiveFields(List<String> v) { this.sensitiveFields = v; return this; }

        public DomainEventMetadata build() {
            return new DomainEventMetadata(
                    name, topic, description, aggregateType, payloadFields, sensitiveFields);
        }
    }
}
