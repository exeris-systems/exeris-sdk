package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Metadata for a {@code @Projection} — a read-only view built from an
 * aggregate's events (the Query side of CQRS).
 *
 * <p>The record originally carried only {@code name} / {@code description} /
 * {@code fields} / {@code cacheable}: it could say <em>what</em> the view shows
 * but not <em>what it is a view of</em>. The 0.7.0 growth adds the source and
 * read-model framing so the "expose this subset of <em>this</em> aggregate as a
 * read-only view" pattern is expressible — the gap logged under the 0.6.0–0.9.0
 * AST-expressiveness roadmap items:
 *
 * <ul>
 *   <li>{@code aggregateTypes} — the source aggregate(s) the projection reads
 *       (from {@code @Projection.aggregateTypes}); the "of <em>this</em>
 *       aggregate" link that {@code fields} (the exposed subset) was missing.</li>
 *   <li>{@code events} / {@code eventClassNames} / {@code topicPattern} — the
 *       event subscription that drives the read model.</li>
 *   <li>{@code model} / {@code schema} — the read-model identity (the view class
 *       and its database schema).</li>
 * </ul>
 *
 * <p><strong>Scope.</strong> {@code @Projection} carries a large operational
 * surface (partitioning, error handling, rebuild strategy, consistency,
 * checkpointing, monitoring, lifecycle hooks, the query API). That is
 * deliberately left out of the AST for now — it is additive and can grow onto
 * this record later without a wire break (by-name JSON,
 * {@code @JsonInclude(NON_NULL)}). Class-valued attributes ({@code model},
 * {@code eventClasses}) are held as their source-written {@code String} name
 * form — the same zero-coupling discipline the capability and event-handler
 * records use, so {@code source-model} never pulls the annotation module's
 * types.
 *
 * @author Exeris SDK Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectionMetadata(
        // ── identity ──────────────────────────────────────────────────────────
        String name,
        String description,
        // ── source linkage ────────────────────────────────────────────────────
        List<String> aggregateTypes,
        // ── event subscription (drives the read model) ────────────────────────
        List<String> events,
        List<String> eventClassNames,
        String topicPattern,
        // ── read model ────────────────────────────────────────────────────────
        String model,
        String schema,
        // ── exposed field subset of the source ────────────────────────────────
        List<String> fields,
        boolean cacheable
) {

    /**
     * Normalizes blank strings to {@code null} (so {@code @JsonInclude(NON_NULL)}
     * drops an annotation's empty-string default) and null lists to empty lists
     * (stable wire form whether built via the builder or constructed directly).
     */
    public ProjectionMetadata {
        description = blankToNull(description);
        topicPattern = blankToNull(topicPattern);
        model = blankToNull(model);
        schema = blankToNull(schema);
        aggregateTypes = (aggregateTypes == null) ? List.of() : List.copyOf(aggregateTypes);
        events = (events == null) ? List.of() : List.copyOf(events);
        eventClassNames = (eventClassNames == null) ? List.of() : List.copyOf(eventClassNames);
        fields = (fields == null) ? List.of() : List.copyOf(fields);
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    // ── convenience ───────────────────────────────────────────────────────────

    /**
     * True when the projection names the aggregate(s) it is a view of.
     *
     * @since 0.7.0
     */
    public boolean hasSourceAggregate() {
        return !aggregateTypes.isEmpty();
    }

    /**
     * True when the projection exposes an explicit field subset.
     *
     * @since 0.7.0
     */
    public boolean hasFields() {
        return !fields.isEmpty();
    }

    /**
     * A minimal projection: a named view exposing a field subset, with no source
     * aggregate / subscription declared. Preserves the pre-0.7.0 factory shape.
     */
    public static ProjectionMetadata simple(String name, List<String> fields) {
        return builder(name).fields(fields).build();
    }

    /**
     * A projection of a single source aggregate exposing a field subset — the
     * common "subset of this aggregate as a read-only view" case.
     *
     * @since 0.7.0
     */
    public static ProjectionMetadata of(String name, String aggregateType, List<String> fields) {
        return builder(name).aggregateType(aggregateType).fields(fields).build();
    }

    /** @since 0.7.0 */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** Mirrors the relevant {@code @Projection} attribute defaults. */
    public static final class Builder {
        private final String name;
        private String description = "";
        private List<String> aggregateTypes = List.of();
        private List<String> events = List.of();
        private List<String> eventClassNames = List.of();
        private String topicPattern = "";
        private String model = "";
        private String schema = "";
        private List<String> fields = List.of();
        private boolean cacheable = false;

        private Builder(String name) {
            this.name = name;
        }

        public Builder description(String v) { this.description = v; return this; }
        public Builder aggregateTypes(List<String> v) { this.aggregateTypes = v; return this; }
        public Builder aggregateType(String v) { this.aggregateTypes = List.of(v); return this; }
        public Builder events(List<String> v) { this.events = v; return this; }
        public Builder eventClassNames(List<String> v) { this.eventClassNames = v; return this; }
        public Builder topicPattern(String v) { this.topicPattern = v; return this; }
        public Builder model(String v) { this.model = v; return this; }
        public Builder schema(String v) { this.schema = v; return this; }
        public Builder fields(List<String> v) { this.fields = v; return this; }
        public Builder cacheable(boolean v) { this.cacheable = v; return this; }

        public ProjectionMetadata build() {
            return new ProjectionMetadata(
                    name, description, aggregateTypes, events, eventClassNames,
                    topicPattern, model, schema, fields, cacheable);
        }
    }
}
