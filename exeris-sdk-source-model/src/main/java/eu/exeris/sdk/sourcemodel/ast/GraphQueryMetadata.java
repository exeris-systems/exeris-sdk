package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata for graph queries.
 * @param name the query's identity
 * @param cypher the traversal, written in Cypher
 * @param description human-readable prose for generated documentation
 * @since 0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphQueryMetadata(String name, String cypher, String description) {}

