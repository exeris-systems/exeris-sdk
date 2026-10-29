package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Metadata for graph database entities.
 *
 * @param label the node label the entity is mirrored to in the graph store
 * @param properties the entity fields carried onto the node, one per field carrying
 *        {@code @GraphProperty}, in field declaration order; an empty list when the entity
 *        declares none. {@code null} means the producer does not extract {@code @GraphProperty},
 *        and a consumer reading this component handles both
 * @param edges the edges leaving the node
 * @param queries the traversals declared against it; {@code null} when they are not carried, which
 *     is what both the processor and the {@code -io} reader write, because neither extracts
 *     {@code @GraphQuery}. {@code null} stays off the wire and means "not carried"; an empty list
 *     claims the entity declares none, and a consumer reading this component handles both
 * @since 0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphMetadata(
        String label,
        List<GraphPropertyMetadata> properties,
        List<GraphEdgeMetadata> edges,
        List<GraphQueryMetadata> queries
) {
    /**
     * Creates a minimal {@code GraphMetadata}, with only the essentials set.
     *
     * @param label the {@code label} the result carries
     * @return the {@code GraphMetadata}
     */
    public static GraphMetadata simple(String label) {
        return new GraphMetadata(label, List.of(), List.of(), List.of());
    }
}

