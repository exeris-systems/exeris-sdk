---
title: Model a graph
type: howto
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Model a graph

**Audience:** you are projecting an entity into a graph store and need to know which graph declarations the reader carries.

**Goal:** declare a node, its properties and its edges on one entity, and read them back.

> **Verified against** `0.13.0-SNAPSHOT` at commit `0753b7d0`, 2026-10-29, on `main`. Snippets are quoted or adapted from the cited class, by path and symbol. If a snippet and its source disagree, **the source wins and this guide is the defect**.

## Prerequisites

- [01 Platform and dependencies](01-platform-and-dependencies.md) done, and an entity as in [02 Model a domain](02-model-a-domain.md).
- The `graph` example under `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/graph/`: `Product` and `Category`.
- A clear scope: the `@Graph` family is about graph-database projection. There is no GraphQL emitter, and `@ExerisDomain(graphqlApi = true)` is read by no generator.

## Steps

### 1. Label the node

`@Graph(nodeClass = ...)` turns an entity into a node and sets the node label. The label defaults to the entity's simple name; `Product` sets `CatalogItem`, so the label you read back is the declared one. `Category` carries no `@Graph`, so the reader returns `null` graph metadata for it, and it appears only as an edge target.

Source: `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/graph/Product.java`, the `Product` class declaration (quoted).

```java
@ExerisDomain(module = "catalog", path = "/products", description = "A product for sale")
@Graph(nodeClass = "CatalogItem")
public class Product {
```

### 2. Choose the properties that become node properties

Only fields marked `@GraphProperty` are node properties. `name` renames the property, `graphType` sets its type and `indexed` marks it indexed. No generator reads these yet. `note` carries no `@GraphProperty`, so it stays out of the node.

Source: `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/graph/Product.java`, the `name`, `price` and `note` fields (adapted — `@PrimaryKey` field and imports removed).

```java
@Field(label = "Name", required = true)
@GraphProperty(name = "title", indexed = true)
private String name;

@Field(label = "Price")
@GraphProperty(graphType = GraphProperty.GraphType.DOUBLE)
private BigDecimal price;

@Field(label = "Internal note")
private String note;
```

The reader carries `name`, `graphType` and `indexed`, and keeps the entity field beside a renamed property. `@GraphProperty` is PARTIAL: nothing else on it is read, and it is read only on an entity that also declares `@Graph`.

### 3. Declare the edges

`@GraphEdge` sits on the field that holds the neighbour. `type` is the relation type, and `target` is the entity class, which the reader turns into the target label. The edge name is the field name.

Source: `exeris-sdk-examples/src/main/java/eu/exeris/sdk/examples/graph/Product.java`, the `category` and `related` fields (quoted).

```java
@GraphEdge(type = "BELONGS_TO", target = Category.class)
private Category category;

@GraphEdge(type = "RELATED_TO", target = Product.class, description = "Bought together")
private List<Product> related;
```

### 4. Leave the kernel-model edge attributes alone

`@GraphEdge` also has attributes that encode the kernel graph SPI, such as direction and weighting. They are at the preview tier. A 1.x minor may change or drop them, and you should pin the exact SDK version if you use one. This example uses none, and stays on `type`, `target` and `description`, which are frozen as declared.

The raw `direction` of `related` is `null` because the source declares none. `effectiveDirection()` applies the default and returns `OUTGOING`. The ruling and the list of those attributes live in [MIGRATION section 2](../../MIGRATION-0.x-to-1.0.md#2-what-is-deliberately-not-removed-carried-through-1x) and the [stability matrix](../stability-matrix.md), and the kernel side is [kernel ADR-102](https://github.com/exeris-systems/exeris-kernel/blob/main/docs/adr/ADR-102-graph-spi-anchor-and-multi-hop-traversal.md).

### 5. Read the graph back

Source: `exeris-sdk-examples/src/test/java/eu/exeris/sdk/examples/graph/GraphExampleTest.java`, the `node`, `properties` and `edges` tests (adapted — lines of the three tests joined under one `graph` variable, with the `related`, `description` and `effectiveField` assertions removed).

```java
GraphMetadata graph = read("Product").graphMetadata();

assertThat(graph.label()).isEqualTo("CatalogItem");
assertThat(graph.properties()).containsExactly(
        new GraphPropertyMetadata("title", null, true, "name"),
        new GraphPropertyMetadata("price", "DOUBLE", false, null));
assertThat(graph.edges()).extracting(GraphEdgeMetadata::name)
        .containsExactly("category", "related");

GraphEdgeMetadata category = graph.edges().getFirst();
assertThat(category.relationType()).isEqualTo("BELONGS_TO");
assertThat(category.targetLabel()).isEqualTo("Category");
```

The last argument of `GraphPropertyMetadata` is the entity field. It is set for `title` because the property was renamed, and `null` for `price`, whose name is the field name. The `read` helper is the one from [guide 02](02-model-a-domain.md).

## Verify

```bash
mvn -pl exeris-sdk-examples -am verify
```

Run it from the repository root. Expect `BUILD SUCCESS`. `GraphExampleTest` asserts the label, the properties, both edges, the absence of queries, and a JSON round trip.

## Not available today

- **`@GraphQuery`.** It is RESERVED. `Product` declares one, `neighbours`, in the source, and it compiles. The reader does not extract it, so `GraphMetadata.queries` is `null`, and the test accepts `null` or empty. `unmodeledFacets` is empty for the file, so its silence says nothing about queries.
- **More than one edge on a field.** `@GraphEdge` is repeatable, and the reader drops a field that declares more than one. Declare one edge per field.
- **Graph store behavior.** The metadata does not say how a store creates indexes or traverses edges. That belongs to the kernel graph SPI and the tooling generators.

The tier of each graph annotation is in the [stability matrix](../stability-matrix.md).

## Where this does not apply

- **Relational relationships.** `@Relationship` models entity links in the domain, as in guide [02](02-model-a-domain.md). `@GraphEdge` models a projection of them into a graph and does not replace them.
- **Graph query execution.** No query declared through the SDK runs today.
- **Trade-off you accept.** The preview edge attributes let you describe a richer graph now, and a 1.x minor may change them under you. Leaving them out keeps your declarations inside the frozen surface and gives up weighting and direction control.
