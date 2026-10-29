/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.graph;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Field;
import eu.exeris.sdk.annotation.Graph;
import eu.exeris.sdk.annotation.GraphEdge;
import eu.exeris.sdk.annotation.GraphProperty;
import eu.exeris.sdk.annotation.GraphQuery;
import eu.exeris.sdk.annotation.system.PrimaryKey;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A product mirrored into the graph store as a node, with typed edges to the nodes it relates to.
 *
 * <p>{@code @Graph} and {@code @GraphEdge} are LIVE and {@code @GraphProperty} is PARTIAL: the
 * reader carries its name, graph type and index flag only. {@code @GraphQuery} is RESERVED: it
 * compiles, and the reader does not extract it. {@code @PrimaryKey} is PARTIAL: the reader carries
 * the marker and the build-time processor does not read it yet.
 */
@ExerisDomain(module = "catalog", path = "/products", description = "A product for sale")
@Graph(nodeClass = "CatalogItem")
public class Product {

    @PrimaryKey
    private UUID id;

    @Field(label = "Name", required = true)
    @GraphProperty(name = "title", indexed = true)
    private String name;

    @Field(label = "Price")
    @GraphProperty(graphType = GraphProperty.GraphType.DOUBLE)
    private BigDecimal price;

    @Field(label = "Internal note")
    private String note;

    @GraphEdge(type = "BELONGS_TO", target = Category.class)
    private Category category;

    @GraphEdge(type = "RELATED_TO", target = Product.class, description = "Bought together")
    private List<Product> related;

    /** Products whose nodes are one hop from this one. */
    @GraphQuery(type = GraphQuery.QueryType.NEIGHBORS, name = "neighbours", maxDepth = 1)
    public List<Product> neighbours() {
        return List.of();
    }
}
