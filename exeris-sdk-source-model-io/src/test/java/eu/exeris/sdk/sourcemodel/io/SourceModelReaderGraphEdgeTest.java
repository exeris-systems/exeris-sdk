package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.GraphEdgeMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphEdgeMetadata.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code @GraphEdge} attributes {@link GraphEdgeMetadata} carries beyond identity, target and
 * relation type: read present-only, so an undeclared attribute is {@code null}.
 */
@DisplayName("SourceModelReader: @GraphEdge direction, weight and description")
class SourceModelReaderGraphEdgeTest {

    private final SourceModelReader reader = new SourceModelReader();

    private List<GraphEdgeMetadata> edges(String fieldDeclarations) {
        String source = """
                package x;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.Graph;
                import eu.exeris.sdk.annotation.GraphEdge;
                import eu.exeris.sdk.annotation.GraphEdge.Direction;
                @ExerisDomain
                @Graph
                public class A {
                %s
                }
                """.formatted(fieldDeclarations);
        return reader.read(source).orElseThrow().graphMetadata().edges();
    }

    @Test
    void everyCarriedAttributeIsRead() {
        List<GraphEdgeMetadata> edges = edges("""
                    @GraphEdge(type = "RATED", targetLabel = "Product",
                            direction = GraphEdge.Direction.INCOMING, bidirectional = true,
                            inverseType = "RATED_BY", description = "Ratings left by users",
                            weighted = true, weightField = "rating", staticWeight = 2.5)
                    private Object rated;
                """);

        assertThat(edges).containsExactly(new GraphEdgeMetadata("rated", "Product", "RATED", null,
                Direction.INCOMING, true, "RATED_BY", "Ratings left by users", true, "rating", 2.5));
    }

    @Test
    void anUndeclaredAttributeIsNullAndADeclaredDefaultIsCarried() {
        List<GraphEdgeMetadata> edges = edges("""
                    @GraphEdge(type = "OWNS")
                    private Object car;
                    @GraphEdge(type = "KNOWS", direction = Direction.OUTGOING, bidirectional = false,
                            weighted = false, staticWeight = 0)
                    private Object friend;
                """);

        assertThat(edges).containsExactly(
                new GraphEdgeMetadata("car", null, "OWNS"),
                new GraphEdgeMetadata("friend", null, "KNOWS", null, Direction.OUTGOING, false,
                        null, null, false, null, 0.0));
    }

    @Test
    void blankStringsAndUnreadableValuesReadAsAbsent() {
        List<GraphEdgeMetadata> edges = edges("""
                    @GraphEdge(type = "T", inverseType = " ", description = "", weightField = "  ",
                            direction = Direction.SIDEWAYS, staticWeight = WEIGHT)
                    private Object edge;
                """);

        assertThat(edges).containsExactly(new GraphEdgeMetadata("edge", null, "T"));
    }

    @Test
    void aNegativeWeightIsReadSigned() {
        List<GraphEdgeMetadata> edges = edges("""
                    @GraphEdge(type = "A", staticWeight = -1.5d) private Object a;
                    @GraphEdge(type = "B", staticWeight = -3) private Object b;
                    @GraphEdge(type = "C", staticWeight = +3) private Object c;
                """);

        assertThat(edges).extracting(GraphEdgeMetadata::staticWeight).containsExactly(-1.5, -3.0, null);
    }
}
