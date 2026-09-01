package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Metadata for graph database entities.
 *
 * @param label the node label the entity is mirrored to in the graph store
 * @param properties the entity fields carried onto the node
 * @param edges the edges leaving the node
 * @param queries the traversals declared against it
 * @author Exeris SDK Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphMetadata(
        String label,
        List<GraphPropertyMetadata> properties,
        List<GraphEdgeMetadata> edges,
        List<GraphQueryMetadata> queries
) {
    public static GraphMetadata simple(String label) {
        return new GraphMetadata(label, List.of(), List.of(), List.of());
    }
}

