package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Metadata for event sourcing configuration.
 * Supports full event sourcing with snapshots, projections, and archival.
 *
 * @param enabled whether the entity is reconstructed from its event stream rather than stored
 *        as rows
 * @param aggregateType the aggregate the stream belongs to
 * @param eventStore the store the stream is persisted in
 * @param snapshotEvery how many events pass between snapshots
 * @param snapshotStrategy how snapshots are taken
 * @param retentionPolicy how long events are kept
 * @param compactEvents whether superseded events are compacted away
 * @param archiveAfterDays how long before events move to archival storage
 * @param eventHandlers the handlers applied when rebuilding state from the stream
 * @param projections the read models maintained from the stream
 * @param versionField the field carrying the aggregate's stream version
 * @param conflictResolution how concurrent appends to one stream are reconciled
 * @author Exeris SDK Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventSourcedMetadata(
        boolean enabled,
        String aggregateType,
        String eventStore,
        int snapshotEvery,
        SnapshotStrategy snapshotStrategy,
        String retentionPolicy,
        boolean compactEvents,
        int archiveAfterDays,
        List<String> eventHandlers,
        List<ProjectionConfig> projections,
        String versionField,
        ConflictResolution conflictResolution
) {
    public static EventSourcedMetadata enabled(String aggregateType) {
        return new EventSourcedMetadata(true, aggregateType, null, 100,
                SnapshotStrategy.COUNT_BASED, null, false, 0, List.of(), List.of(),
                "version", ConflictResolution.OPTIMISTIC_LOCK);
    }

    public static EventSourcedMetadata disabled() {
        return new EventSourcedMetadata(false, null, null, 0, null, null, false, 0,
                List.of(), List.of(), null, null);
    }

    public static Builder builder(String aggregateType) {
        return new Builder(aggregateType);
    }

    public boolean hasProjections() { return projections != null && !projections.isEmpty(); }
    public boolean hasEventHandlers() { return eventHandlers != null && !eventHandlers.isEmpty(); }

    /**

     * How snapshots of a rebuilt aggregate are taken.

     */

    public enum SnapshotStrategy {
        /** Take snapshot every N events */
        COUNT_BASED,
        /** Take snapshot every N seconds */
        TIME_BASED,
        /** Take snapshot when aggregate size exceeds threshold */
        SIZE_BASED,
        /** No automatic snapshots */
        MANUAL,
        /** Adaptive based on access patterns */
        ADAPTIVE
    }

    /**

     * How concurrent appends to one aggregate's event stream are reconciled.

     */

    public enum ConflictResolution {
        /** Optimistic locking with version check */
        OPTIMISTIC_LOCK,
        /** Last write wins */
        LAST_WRITE_WINS,
        /** Merge conflicting events */
        MERGE,
        /** Custom resolution handler */
        CUSTOM
    }

    /**
     * Configuration for event projections.
     * @param name the projection's identity
     * @param targetType the read model the projection maintains
     * @param eventTypes the events the projection consumes
     * @param handler the handler applying those events to the read model
     * @param async whether the projection is updated off the publishing thread
     * @param topic the messaging topic the projection subscribes to
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProjectionConfig(
            String name,
            String targetType,
            List<String> eventTypes,
            String handler,
            boolean async,
            String topic
    ) {
        public static ProjectionConfig sync(String name, String targetType, List<String> eventTypes) {
            return new ProjectionConfig(name, targetType, eventTypes, null, false, null);
        }

        public static ProjectionConfig async(String name, String targetType, List<String> eventTypes, String topic) {
            return new ProjectionConfig(name, targetType, eventTypes, null, true, topic);
        }
    }

    public static final class Builder {
        private final String aggregateType;
        private boolean enabled = true;
        private String eventStore;
        private int snapshotEvery = 100;
        private SnapshotStrategy snapshotStrategy = SnapshotStrategy.COUNT_BASED;
        private String retentionPolicy;
        private boolean compactEvents = false;
        private int archiveAfterDays = 0;
        private List<String> eventHandlers = List.of();
        private List<ProjectionConfig> projections = List.of();
        private String versionField = "version";
        private ConflictResolution conflictResolution = ConflictResolution.OPTIMISTIC_LOCK;

        private Builder(String aggregateType) { this.aggregateType = aggregateType; }

        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder eventStore(String v) { this.eventStore = v; return this; }
        public Builder snapshotEvery(int v) { this.snapshotEvery = v; return this; }
        public Builder snapshotStrategy(SnapshotStrategy v) { this.snapshotStrategy = v; return this; }
        public Builder retentionPolicy(String v) { this.retentionPolicy = v; return this; }
        public Builder compactEvents(boolean v) { this.compactEvents = v; return this; }
        public Builder archiveAfterDays(int v) { this.archiveAfterDays = v; return this; }
        public Builder eventHandlers(List<String> v) { this.eventHandlers = v; return this; }
        public Builder projections(List<ProjectionConfig> v) { this.projections = v; return this; }
        public Builder versionField(String v) { this.versionField = v; return this; }
        public Builder conflictResolution(ConflictResolution v) { this.conflictResolution = v; return this; }

        public EventSourcedMetadata build() {
            return new EventSourcedMetadata(enabled, aggregateType, eventStore, snapshotEvery,
                    snapshotStrategy, retentionPolicy, compactEvents, archiveAfterDays,
                    eventHandlers, projections, versionField, conflictResolution);
        }
    }
}

