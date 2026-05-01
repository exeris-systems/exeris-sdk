package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Metadata for graph database entities.
 *
 * @author Exeris Team
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

