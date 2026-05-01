package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata for graph queries.
 * @author Exeris Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphQueryMetadata(String name, String cypher, String description) {}

