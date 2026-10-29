package eu.exeris.sdk.sourcemodel.ast;

import eu.exeris.sdk.sourcemodel.ast.GraphEdgeMetadata.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GraphEdgeMetadata")
class GraphEdgeMetadataTest {

    private static GraphEdgeMetadata edge(String name, String field, Direction direction, Boolean bidirectional) {
        return new GraphEdgeMetadata(name, "User", "FRIEND_OF", field, direction, bidirectional,
                null, null, null, null, null);
    }

    @Test
    void twoEdgesOnOneFieldKeepDistinctIdentitiesAndShareTheField() {
        GraphEdgeMetadata friend = edge("friendsFriendOf", "friends", null, null);
        GraphEdgeMetadata blocked = edge("friendsBlocked", "friends", null, null);

        assertThat(friend.name()).isNotEqualTo(blocked.name());
        assertThat(friend.effectiveField()).isEqualTo("friends");
        assertThat(blocked.effectiveField()).isEqualTo("friends");
    }

    @Test
    void theThreeArgumentShapeIsItsOwnField() {
        GraphEdgeMetadata single = new GraphEdgeMetadata("owner", "User", "OWNED_BY");
        assertThat(single.field()).isNull();
        assertThat(single.effectiveField()).isEqualTo("owner");
        assertThat(single.effectiveDirection()).isEqualTo(Direction.OUTGOING);
    }

    @Test
    void aFieldEqualToTheNameIsTheSameValueAsNoField() {
        // One canonical form for "the field is the identity", so a producer that writes the
        // field anyway and one that omits it produce equal metadata and the same JSON.
        assertThat(edge("owner", "owner", null, null)).isEqualTo(edge("owner", null, null, null));
        assertThat(edge("owner", "owner", null, null).field()).isNull();
    }

    @Test
    void blankStringsNormaliseToNull() {
        GraphEdgeMetadata blank = new GraphEdgeMetadata("e", "User", "T", " ", null, null,
                "", "\t", null, " ", null);
        assertThat(blank.field()).isNull();
        assertThat(blank.inverseType()).isNull();
        assertThat(blank.description()).isNull();
        assertThat(blank.weightField()).isNull();
    }

    @Test
    void effectiveDirectionFollowsTheKernelRule() {
        // GraphEdgeDescriptor: effective direction = bidirectional ? BOTH : direction.
        assertThat(edge("e", null, Direction.INCOMING, null).effectiveDirection()).isEqualTo(Direction.INCOMING);
        assertThat(edge("e", null, Direction.INCOMING, false).effectiveDirection()).isEqualTo(Direction.INCOMING);
        assertThat(edge("e", null, Direction.INCOMING, true).effectiveDirection()).isEqualTo(Direction.BOTH);
        assertThat(edge("e", null, null, true).effectiveDirection()).isEqualTo(Direction.BOTH);
        assertThat(edge("e", null, Direction.BOTH, null).effectiveDirection()).isEqualTo(Direction.BOTH);
        assertThat(edge("e", null, null, false).effectiveDirection()).isEqualTo(Direction.OUTGOING);
    }
}
