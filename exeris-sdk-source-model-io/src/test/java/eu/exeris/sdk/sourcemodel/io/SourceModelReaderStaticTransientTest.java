package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphPropertyMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.ast.UIMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("reader: a static or transient field is read by no facet, as the processor skips it")
class SourceModelReaderStaticTransientTest {

    private static final String DRONE = """
            package x;
            import eu.exeris.sdk.annotation.DomainEvent;
            import eu.exeris.sdk.annotation.DomainEvent.Trigger;
            import eu.exeris.sdk.annotation.ExerisDomain;
            import eu.exeris.sdk.annotation.Field;
            import eu.exeris.sdk.annotation.Graph;
            import eu.exeris.sdk.annotation.GraphProperty;
            import eu.exeris.sdk.annotation.Relationship;
            import eu.exeris.sdk.annotation.UIGroup;
            import eu.exeris.sdk.annotation.system.AuditCreatedAt;

            @ExerisDomain
            @Graph
            @DomainEvent(name = "DroneCharged", trigger = Trigger.UPDATE, topic = "drones")
            public class Drone {
                public static final String CHARGING = "CHARGING";

                @Field @GraphProperty @UIGroup(name = "status")
                private static String lastStatus;

                @Field @GraphProperty @UIGroup(name = "status")
                private transient String sessionToken;

                @Relationship(targetEntity = Hangar.class, displayField = "name")
                private static Hangar defaultHangar;

                @AuditCreatedAt
                private transient java.time.Instant cachedAt;

                @Field @GraphProperty @UIGroup(name = "status")
                private String status;
            }
            """;

    private final DomainMetadata drone = new SourceModelReader().read(DRONE).orElseThrow();

    @Test
    void fieldsHoldOnlyInstanceState() {
        assertThat(drone.fields()).extracting(FieldMetadata::name).containsExactly("status");
    }

    @Test
    void eventPayloadHoldsOnlyInstanceFields() {
        assertThat(drone.events()).singleElement()
                .satisfies(event -> assertThat(event.payloadFields()).containsExactly("status"));
    }

    @Test
    void aStaticRelationshipIsNotRead() {
        assertThat(drone.relationships()).extracting(RelationshipMetadata::fieldName).isEmpty();
    }

    @Test
    void aTransientSystemMarkerIsNotRead() {
        assertThat(drone.systemFields() == null ? null : drone.systemFields().createdAtField()).isNull();
    }

    @Test
    void graphAndLayoutMembersHoldOnlyInstanceFields() {
        assertThat(drone.graphMetadata().properties())
                .extracting(GraphPropertyMetadata::name).containsExactly("status");
        assertThat(drone.uiMetadata().groups()).singleElement()
                .satisfies(group -> assertThat(group).extracting(UIMetadata.UIGroupMetadata::fields)
                        .asList().containsExactly("status"));
    }
}
