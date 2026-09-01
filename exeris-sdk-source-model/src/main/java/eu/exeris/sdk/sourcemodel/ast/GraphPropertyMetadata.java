package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata for graph properties.
 * @param name the property's name on the graph node
 * @param type the property's type in the graph store
 * @param indexed whether the graph store indexes the property
 * @author Exeris SDK Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphPropertyMetadata(String name, String type, boolean indexed) {}

