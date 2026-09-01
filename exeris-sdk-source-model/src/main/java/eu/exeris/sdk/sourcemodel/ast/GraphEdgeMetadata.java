package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata for graph edges (relationships).
 * @param name the edge's identity
 * @param targetLabel the node label the edge points at
 * @param relationType the relationship the edge represents
 * @author Exeris SDK Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphEdgeMetadata(String name, String targetLabel, String relationType) {}

