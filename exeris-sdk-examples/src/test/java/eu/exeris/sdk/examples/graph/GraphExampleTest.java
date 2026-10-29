/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.graph;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphEdgeMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphMetadata;
import eu.exeris.sdk.sourcemodel.ast.GraphPropertyMetadata;
import eu.exeris.sdk.sourcemodel.io.SourceModelReader;
import eu.exeris.sdk.tck.TckMappers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the graph example sources exactly as the documentation quotes them and asserts the
 * metadata they declare. {@code @GraphQuery} is a reserved surface the reader does not extract,
 * so the tests pin its absence from the read model.
 */
@DisplayName("examples: graph")
class GraphExampleTest {

    private static final Path SOURCES = Path.of("src/main/java/eu/exeris/sdk/examples/graph");

    private final SourceModelReader reader = new SourceModelReader();

    private DomainMetadata read(String entity) throws IOException {
        return reader.read(Files.readString(SOURCES.resolve(entity + ".java"))).orElseThrow();
    }

    @Test
    @DisplayName("@Graph names the node label")
    void node() throws IOException {
        GraphMetadata graph = read("Product").graphMetadata();

        assertThat(graph.label()).isEqualTo("CatalogItem");
        assertThat(read("Category").graphMetadata()).isNull();
    }

    @Test
    @DisplayName("@GraphProperty renames a property and sets its type and index flag")
    void properties() throws IOException {
        GraphMetadata graph = read("Product").graphMetadata();

        assertThat(graph.properties()).containsExactly(
                new GraphPropertyMetadata("title", null, true, "name"),
                new GraphPropertyMetadata("price", "DOUBLE", false, null));
        assertThat(graph.properties().getFirst().effectiveField()).isEqualTo("name");
        assertThat(graph.properties()).extracting(GraphPropertyMetadata::name)
                .doesNotContain("note");
    }

    @Test
    @DisplayName("@GraphEdge carries the relation type and the target label")
    void edges() throws IOException {
        GraphMetadata graph = read("Product").graphMetadata();

        assertThat(graph.edges()).extracting(GraphEdgeMetadata::name)
                .containsExactly("category", "related");

        GraphEdgeMetadata category = graph.edges().getFirst();
        assertThat(category.relationType()).isEqualTo("BELONGS_TO");
        assertThat(category.targetLabel()).isEqualTo("Category");
        assertThat(category.description()).isNull();

        GraphEdgeMetadata related = graph.edges().get(1);
        assertThat(related.relationType()).isEqualTo("RELATED_TO");
        assertThat(related.targetLabel()).isEqualTo("Product");
        assertThat(related.description()).isEqualTo("Bought together");
        assertThat(related.effectiveDirection()).isEqualTo(GraphEdgeMetadata.Direction.OUTGOING);
    }

    @Test
    @DisplayName("reserved @GraphQuery is declared but not read")
    void queriesAreNotRead() throws IOException {
        assertThat(read("Product").graphMetadata().queries()).isNullOrEmpty();
        assertThat(reader.unmodeledFacets(Files.readString(SOURCES.resolve("Product.java")))).isEmpty();
    }

    @Test
    @DisplayName("the metadata survives a JSON round trip")
    void jsonRoundTrip() throws IOException {
        ObjectMapper mapper = TckMappers.canonical();
        DomainMetadata product = read("Product");

        String json = mapper.writeValueAsString(product);

        assertThat(mapper.readValue(json, DomainMetadata.class)).isEqualTo(product);
    }
}
