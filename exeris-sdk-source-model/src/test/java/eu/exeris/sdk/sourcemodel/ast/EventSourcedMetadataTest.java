package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventSourcedMetadata + ProjectionConfig")
class EventSourcedMetadataTest {

    @Test
    void enabledFactorySetsExpectedDefaults() {
        EventSourcedMetadata e = EventSourcedMetadata.enabled("Order");
        assertThat(e.enabled()).isTrue();
        assertThat(e.aggregateType()).isEqualTo("Order");
        assertThat(e.snapshotEvery()).isEqualTo(100);
        assertThat(e.snapshotStrategy()).isEqualTo(EventSourcedMetadata.SnapshotStrategy.COUNT_BASED);
        assertThat(e.conflictResolution()).isEqualTo(EventSourcedMetadata.ConflictResolution.OPTIMISTIC_LOCK);
        assertThat(e.versionField()).isEqualTo("version");
        assertThat(e.eventHandlers()).isEmpty();
        assertThat(e.projections()).isEmpty();
    }

    @Test
    void disabledFactorySetsAllOff() {
        EventSourcedMetadata d = EventSourcedMetadata.disabled();
        assertThat(d.enabled()).isFalse();
        assertThat(d.aggregateType()).isNull();
        assertThat(d.snapshotStrategy()).isNull();
        assertThat(d.conflictResolution()).isNull();
        assertThat(d.versionField()).isNull();
    }

    @Test
    void predicatesReflectCollectionContent() {
        EventSourcedMetadata empty = EventSourcedMetadata.disabled();
        assertThat(empty.hasProjections()).isFalse();
        assertThat(empty.hasEventHandlers()).isFalse();

        EventSourcedMetadata withBoth = EventSourcedMetadata.builder("Order")
                .eventHandlers(List.of("OrderCreatedHandler"))
                .projections(List.of(EventSourcedMetadata.ProjectionConfig.sync(
                        "summary", "OrderSummary", List.of("OrderCreated"))))
                .build();
        assertThat(withBoth.hasProjections()).isTrue();
        assertThat(withBoth.hasEventHandlers()).isTrue();
    }

    @Test
    void predicatesHandleNullCollections() {
        EventSourcedMetadata nullLists = new EventSourcedMetadata(true, "Order", null, 0,
                EventSourcedMetadata.SnapshotStrategy.MANUAL, null, false, 0,
                null, null, "version", EventSourcedMetadata.ConflictResolution.LAST_WRITE_WINS);
        assertThat(nullLists.hasEventHandlers()).isFalse();
        assertThat(nullLists.hasProjections()).isFalse();
    }

    @Test
    void enumsExposeAllStrategies() {
        assertThat(EventSourcedMetadata.SnapshotStrategy.values())
                .containsExactly(
                        EventSourcedMetadata.SnapshotStrategy.COUNT_BASED,
                        EventSourcedMetadata.SnapshotStrategy.TIME_BASED,
                        EventSourcedMetadata.SnapshotStrategy.SIZE_BASED,
                        EventSourcedMetadata.SnapshotStrategy.MANUAL,
                        EventSourcedMetadata.SnapshotStrategy.ADAPTIVE);
        assertThat(EventSourcedMetadata.ConflictResolution.values())
                .containsExactly(
                        EventSourcedMetadata.ConflictResolution.OPTIMISTIC_LOCK,
                        EventSourcedMetadata.ConflictResolution.LAST_WRITE_WINS,
                        EventSourcedMetadata.ConflictResolution.MERGE,
                        EventSourcedMetadata.ConflictResolution.CUSTOM);
    }

    @Test
    void projectionConfigSyncFactory() {
        EventSourcedMetadata.ProjectionConfig p = EventSourcedMetadata.ProjectionConfig.sync(
                "summary", "OrderSummary", List.of("OrderCreated", "OrderUpdated"));
        assertThat(p.name()).isEqualTo("summary");
        assertThat(p.targetType()).isEqualTo("OrderSummary");
        assertThat(p.eventTypes()).containsExactly("OrderCreated", "OrderUpdated");
        assertThat(p.async()).isFalse();
        assertThat(p.topic()).isNull();
    }

    @Test
    void projectionConfigAsyncFactory() {
        EventSourcedMetadata.ProjectionConfig p = EventSourcedMetadata.ProjectionConfig.async(
                "audit", "OrderAudit", List.of("OrderCreated"), "audit-topic");
        assertThat(p.async()).isTrue();
        assertThat(p.topic()).isEqualTo("audit-topic");
    }

    @Test
    void builderPropagatesEverySetter() {
        EventSourcedMetadata.ProjectionConfig p = EventSourcedMetadata.ProjectionConfig.sync(
                "summary", "OrderSummary", List.of("OrderCreated"));
        EventSourcedMetadata e = EventSourcedMetadata.builder("Order")
                .enabled(false)
                .eventStore("kafka")
                .snapshotEvery(50)
                .snapshotStrategy(EventSourcedMetadata.SnapshotStrategy.TIME_BASED)
                .retentionPolicy("P30D")
                .compactEvents(true)
                .archiveAfterDays(90)
                .eventHandlers(List.of("H1"))
                .projections(List.of(p))
                .versionField("rev")
                .conflictResolution(EventSourcedMetadata.ConflictResolution.MERGE)
                .build();
        assertThat(e.enabled()).isFalse();
        assertThat(e.eventStore()).isEqualTo("kafka");
        assertThat(e.snapshotEvery()).isEqualTo(50);
        assertThat(e.snapshotStrategy()).isEqualTo(EventSourcedMetadata.SnapshotStrategy.TIME_BASED);
        assertThat(e.retentionPolicy()).isEqualTo("P30D");
        assertThat(e.compactEvents()).isTrue();
        assertThat(e.archiveAfterDays()).isEqualTo(90);
        assertThat(e.eventHandlers()).containsExactly("H1");
        assertThat(e.projections()).containsExactly(p);
        assertThat(e.versionField()).isEqualTo("rev");
        assertThat(e.conflictResolution()).isEqualTo(EventSourcedMetadata.ConflictResolution.MERGE);
    }
}
