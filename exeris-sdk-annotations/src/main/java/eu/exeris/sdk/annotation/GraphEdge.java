package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Defines a graph edge (relationship) from this entity to another.
 *
 * <p>Creates edges in the graph database representing relationships
 * between entities. Supports weighted edges, edges traversed both ways,
 * and edge properties.
 *
 * <h2>Basic Usage:</h2>
 * {@snippet lang="java" :
 * @GraphEdge(type = "BELONGS_TO", target = Category.class)
 * private Category category;
 *
 * @GraphEdge(
 *     type = "PURCHASED_BY",
 *     target = Customer.class,
 *     direction = Direction.INCOMING
 * )
 * private Customer customer;
 * }
 *
 * <h2>Weighted Edge:</h2>
 * {@snippet lang="java" :
 * @GraphEdge(
 *     type = "RATED",
 *     target = Product.class,
 *     weighted = true,
 *     weightField = "rating",
 *     properties = {"rating", "reviewDate"}
 * )
 * private Product ratedProduct;
 * }
 *
 * <h2>Bidirectional:</h2>
 * {@snippet lang="java" :
 * @GraphEdge(
 *     type = "FRIEND_OF",
 *     target = User.class,
 *     bidirectional = true
 * )
 * private List<User> friends;
 * }
 *
 * <h2>Two edges on one field:</h2>
 * {@code @GraphEdge} is repeatable. {@code GraphEdgeMetadata} carries each edge under its own
 * identity and names the shared field separately; the {@code exeris-tooling} processor still
 * refuses the repetition at the declaration, and the {@code -io} reader drops it, until the
 * processor extracts it.
 *
 * <p><strong>Status: LIVE</strong> — extracted into {@code GraphEdgeMetadata} by the
 * build-time processor and by the {@code -io} reader alike, and read by
 * {@code KernelGraphSyncGenerator}, so it drives emitted output. Per attribute:
 * <ul>
 *   <li><strong>Carried and read by the generator:</strong> {@link #type()}, and the target
 *       label from {@link #targetLabel()}, else {@link #target()}'s simple name, else
 *       {@link #targetName()}.</li>
 *   <li><strong>Carried, reserved outside the 1.0.0 freeze:</strong> {@link #direction()},
 *       {@link #bidirectional()}, {@link #inverseType()}, {@link #weighted()},
 *       {@link #weightField()} and {@link #staticWeight()} each have a {@code GraphEdgeMetadata}
 *       component and a kernel graph-model field to land in, and no generator reads them yet.
 *       The {@code -io} reader reads them; the processor does not yet. The kernel graph SPI
 *       is at tier {@code preview}, so they are not frozen at 1.0.0 (ADR-072).</li>
 *   <li><strong>Carried, documentation only:</strong> {@link #description()}.</li>
 *   <li><strong>Not carried, reserved outside the 1.0.0 freeze:</strong> {@link #properties()},
 *       {@link #propertyMappings()}, {@link #staticProperties()} and {@link #upsert()}. Each
 *       says why on its own.</li>
 *   <li><strong>Deprecated for removal at 1.0.0:</strong> every other attribute, and the
 *       nested {@link ComputedProperty}. No kernel graph binding exists for any of them.</li>
 * </ul>
 * @since 0.1
 * @see Graph
 * @see GraphProperty
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
@Repeatable(GraphEdges.class)
@SuppressWarnings({"PMD.ExcessivePublicCount", "PMD.TooManyMethods"})
public @interface GraphEdge {

    // ═══════════════════════════════════════════════════════════════════════════
    // EDGE IDENTITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Edge type/label.
     * <p>Relationship type in graph (e.g., "BELONGS_TO", "PURCHASED", "FOLLOWS").
     *
     * @return edge type
     */
    String type();

    /**
     * Target entity class.
     *
     * @return target class
     */
    Class<?> target() default void.class;

    /**
     * Target entity name (if class not available).
     *
     * @return target entity name
     */
    String targetName() default "";

    /**
     * Target node label in graph.
     *
     * @return target label
     */
    String targetLabel() default "";

    /**
     * Edge description, carried as {@code GraphEdgeMetadata.description}. Documentation only:
     * nothing generated or stored depends on it.
     *
     * @return description
     */
    String description() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // DIRECTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Edge direction relative to this entity, carried as {@code GraphEdgeMetadata.direction}
     * and mapping onto the kernel's {@code GraphEdgeDescriptor.direction}. Kernel traversals
     * honour it (kernel ADR-102).
     *
     * <p><strong>Status: RESERVED, outside the 1.0.0 freeze</strong> — carried; no generator
     * reads it yet.
     *
     * @return direction
     */
    Direction direction() default Direction.OUTGOING;

    /**
     * Traverse the edge both ways. The edge is stored once; a traversal follows it as
     * {@link Direction#BOTH} whatever {@link #direction()} says, which is the kernel's rule for
     * {@code GraphEdgeDescriptor.bidirectional} (kernel ADR-102). Carried as
     * {@code GraphEdgeMetadata.bidirectional}.
     *
     * <p><strong>Status: RESERVED, outside the 1.0.0 freeze</strong> — carried; no generator
     * reads it yet.
     *
     * @return true for bidirectional
     */
    boolean bidirectional() default false;

    /**
     * Relation type of a reverse edge, stored from the target back to this entity as a second
     * edge. Empty means no reverse edge is stored. Carried as
     * {@code GraphEdgeMetadata.inverseType}.
     *
     * <p><strong>Status: RESERVED, outside the 1.0.0 freeze</strong> — carried; no generator
     * reads it yet.
     *
     * @return inverse type
     */
    String inverseType() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // EDGE PROPERTIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Entity fields to copy as edge properties.
     *
     * <p><strong>Status: RESERVED, outside the 1.0.0 freeze</strong> — not extracted, and
     * {@code GraphEdgeMetadata} has no component for it. The kernel graph session accepts an
     * edge-property payload ({@code GraphSession.upsertEdge}); the generated graph sync passes
     * none. The component lands with the generator change that encodes the payload.
     *
     * @return property field names
     */
    String[] properties() default {};

    /**
     * Edge property mappings (entity field to edge property).
     *
     * <p><strong>Status: RESERVED, outside the 1.0.0 freeze</strong> — not extracted, and
     * {@code GraphEdgeMetadata} has no component for it. The kernel graph session accepts an
     * edge-property payload ({@code GraphSession.upsertEdge}); the generated graph sync passes
     * none. The component lands with the generator change that encodes the payload.
     *
     * @return property mappings
     */
    PropertyMapping[] propertyMappings() default {};

    /**
     * Static edge properties.
     *
     * <p><strong>Status: RESERVED, outside the 1.0.0 freeze</strong> — not extracted, and
     * {@code GraphEdgeMetadata} has no component for it. The kernel graph session accepts an
     * edge-property payload ({@code GraphSession.upsertEdge}); the generated graph sync passes
     * none. The component lands with the generator change that encodes the payload.
     *
     * @return static properties
     */
    StaticProperty[] staticProperties() default {};

    /**
     * Deprecated, no effect: computes edge properties from SpEL expressions.
     *
     * @return computed properties
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: no generator and no kernel
     *         component evaluates a SpEL expression. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    ComputedProperty[] computedProperties() default {};

    // ═══════════════════════════════════════════════════════════════════════════
    // WEIGHT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Edge is weighted. Carried as {@code GraphEdgeMetadata.weighted}.
     *
     * <p><strong>Status: RESERVED, outside the 1.0.0 freeze</strong> — carried; no generator
     * reads it yet.
     *
     * @return true if weighted
     */
    boolean weighted() default false;

    /**
     * Entity field holding the edge's weight, the value the kernel graph session stores with
     * the edge ({@code GraphSession.upsertEdge}). Carried as
     * {@code GraphEdgeMetadata.weightField}.
     *
     * <p><strong>Status: RESERVED, outside the 1.0.0 freeze</strong> — carried; the generated
     * graph sync writes a weight of {@code 1.0} until a generator reads it.
     *
     * @return weight field name
     */
    String weightField() default "";

    /**
     * Deprecated, no effect: names the graph property the weight is stored under.
     *
     * @return weight property name
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the kernel graph model stores
     *         one weight per edge under a fixed name, so there is no property name to choose
     *         (kernel {@code GraphEdgeDescriptor}). See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String weightProperty() default "weight";

    /**
     * Fixed weight of the edge when no {@link #weightField()} is set, the kernel's
     * {@code GraphEdgeDescriptor.weight}; shortest-path search reads it as the edge's cost.
     * Carried as {@code GraphEdgeMetadata.staticWeight}.
     *
     * <p><strong>Status: RESERVED, outside the 1.0.0 freeze</strong> — carried; no generator
     * reads it yet.
     *
     * @return static weight
     */
    double staticWeight() default 1.0;

    /**
     * Deprecated, no effect: computes the weight from a SpEL expression.
     *
     * @return weight expression
     * @deprecated since 0.13.0, for removal in 1.0.0. Use {@link #weightField()} or {@link
     *         #staticWeight()}. No generator and no kernel component evaluates a SpEL expression.
     *         See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String weightExpression() default "";

    /**
     * Deprecated, no effect: normalises weight values.
     *
     * @return true to normalize
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the kernel stores the weight
     *         it is given and normalises nothing. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean normalizeWeight() default false;

    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRAINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Deprecated, no effect: caps the number of edges of this type from one source node; {@code 0}
     * means unlimited.
     *
     * @return max edges
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the kernel graph session
     *         enforces no edge cardinality ({@code GraphSession} has no constraint operation). See
     *         {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    int maxEdges() default 0;

    /**
     * Deprecated, no effect: requires a minimum number of edges.
     *
     * @return min edges
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the kernel graph session
     *         enforces no edge cardinality ({@code GraphSession} has no constraint operation). See
     *         {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    int minEdges() default 0;

    /**
     * Deprecated, no effect: allows one edge per source-target pair.
     *
     * @return true for unique
     * @deprecated since 0.13.0, for removal in 1.0.0. Use {@link #upsert()}: an upserted edge is
     *         already one per source, target and type. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean unique() default false;

    /**
     * Update existing edge instead of creating new.
     *
     * <p><strong>Status: RESERVED, outside the 1.0.0 freeze</strong> — not extracted, and
     * {@code GraphEdgeMetadata} has no component for it. The kernel graph session offers both
     * operations ({@code GraphSession.createEdge} and {@code .upsertEdge}); the generated graph
     * sync always upserts. The component lands with the generator change that chooses between them.
     *
     * @return true to upsert
     */
    boolean upsert() default true;

    /**
     * Deprecated, no effect: creates the edge only when a SpEL condition holds.
     *
     * @return condition expression
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: no generator and no kernel
     *         component evaluates a SpEL expression. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String condition() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // CASCADE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Deprecated, no effect: deletes the edge when its source is deleted.
     *
     * @return true to cascade
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: deleting a node removes every
     *         edge incident to it, unconditionally ({@code GraphSession.deleteNode}), and the
     *         kernel deletes no other node. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean cascadeDelete() default true;

    /**
     * Deprecated, no effect: deletes the target node when it has no other edge.
     *
     * @return true to cascade delete target
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: deleting a node removes every
     *         edge incident to it, unconditionally ({@code GraphSession.deleteNode}), and the
     *         kernel deletes no other node. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean cascadeDeleteOrphanTarget() default false;

    /**
     * Deprecated, no effect: updates the edge when its source is updated.
     *
     * @return true to update
     * @deprecated since 0.13.0, for removal in 1.0.0. Use {@link #upsert()}: the generated sync
     *         upserts every edge each time it syncs the entity. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean cascadeUpdate() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // INDEXING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Deprecated, no effect: creates an index on this edge type.
     *
     * @return true to index
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the kernel graph dialect
     *         builds edge tables with no index parameter ({@code
     *         GraphDialect.buildCreateEdgeTable}). See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean indexed() default false;

    /**
     * Deprecated, no effect: indexes edge properties.
     *
     * @return properties to index
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the kernel graph dialect
     *         builds edge tables with no index parameter ({@code
     *         GraphDialect.buildCreateEdgeTable}). See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String[] indexedProperties() default {};

    /**
     * Deprecated, no effect: names a composite edge index.
     *
     * @return index name
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the kernel graph dialect
     *         builds edge tables with no index parameter ({@code
     *         GraphDialect.buildCreateEdgeTable}). See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String compositeIndex() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // TEMPORAL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Deprecated, no effect: tracks edge timestamps.
     *
     * @return true to track
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the kernel graph model
     *         carries no timestamps and no validity window on an edge, and filtering a traversal on
     *         an edge property is outside the scope kernel ADR-102 closes. See {@code
     *         MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean temporal() default false;

    /**
     * Deprecated, no effect: names the created-at edge property.
     *
     * @return property name
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the kernel graph model
     *         carries no timestamps and no validity window on an edge, and filtering a traversal on
     *         an edge property is outside the scope kernel ADR-102 closes. See {@code
     *         MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String createdAtProperty() default "createdAt";

    /**
     * Deprecated, no effect: names the updated-at edge property.
     *
     * @return property name
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the kernel graph model
     *         carries no timestamps and no validity window on an edge, and filtering a traversal on
     *         an edge property is outside the scope kernel ADR-102 closes. See {@code
     *         MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String updatedAtProperty() default "updatedAt";

    /**
     * Deprecated, no effect: names the field holding the start of the edge's validity.
     *
     * @return field name
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the kernel graph model
     *         carries no timestamps and no validity window on an edge, and filtering a traversal on
     *         an edge property is outside the scope kernel ADR-102 closes. See {@code
     *         MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String validFrom() default "";

    /**
     * Deprecated, no effect: names the field holding the end of the edge's validity.
     *
     * @return field name
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the kernel graph model
     *         carries no timestamps and no validity window on an edge, and filtering a traversal on
     *         an edge property is outside the scope kernel ADR-102 closes. See {@code
     *         MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String validTo() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // SYNC CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Deprecated, no effect: excludes the edge from graph sync when {@code false}.
     *
     * @return true to sync
     * @deprecated since 0.13.0, for removal in 1.0.0. To keep an edge out of the generated graph
     *         sync, do not declare it. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean sync() default true;

    /**
     * Deprecated, no effect: syncs the edge on named events only.
     *
     * @return event names
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the generated graph sync
     *         writes every declared edge when it syncs the entity, and the kernel graph session has
     *         no event filter and no batch operation. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String[] syncOnEvents() default {};

    /**
     * Deprecated, no effect: syncs edge properties on update.
     *
     * @return true to sync properties
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the generated graph sync
     *         writes every declared edge when it syncs the entity, and the kernel graph session has
     *         no event filter and no batch operation. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean syncProperties() default true;

    /**
     * Deprecated, no effect: batches edge creation.
     *
     * @return true to batch
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the generated graph sync
     *         writes every declared edge when it syncs the entity, and the kernel graph session has
     *         no event filter and no batch operation. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean batch() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // TRAVERSAL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Deprecated, no effect: includes the edge in default traversals.
     *
     * @return true to include
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: the kernel has no default
     *         traversal; every traversal names the edge it follows (kernel ADR-102). See {@code
     *         MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean traversable() default true;

    /**
     * Deprecated, no effect: sets the edge's cost for path algorithms.
     *
     * @return traversal cost
     * @deprecated since 0.13.0, for removal in 1.0.0. Use {@link #staticWeight()}: the kernel's
     *         shortest path reads the edge weight as its cost. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    double traversalCost() default 1.0;

    /**
     * Deprecated, no effect: computes the traversal cost from a SpEL expression.
     *
     * @return cost expression
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: no generator and no kernel
     *         component evaluates a SpEL expression. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String traversalCostExpression() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // GRAPHQL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Deprecated, no effect: exposes the edge in a GraphQL schema.
     *
     * @return true to expose
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: no generator emits a GraphQL
     *         schema, and the kernel has no GraphQL surface. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean graphqlExpose() default true;

    /**
     * Deprecated, no effect: names the edge's GraphQL field.
     *
     * @return field name
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: no generator emits a GraphQL
     *         schema, and the kernel has no GraphQL surface. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String graphqlFieldName() default "";

    /**
     * Deprecated, no effect: describes the edge's GraphQL field.
     *
     * @return description
     * @deprecated since 0.13.0, for removal in 1.0.0. No replacement: no generator emits a GraphQL
     *         schema, and the kernel has no GraphQL surface. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String graphqlDescription() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // ENUMS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Edge direction in the graph, with the meaning of the kernel's
     * {@code GraphEdgeDescriptor.Direction}.
     */
    enum Direction {
        /** Edge goes from this node to target. */
        OUTGOING,

        /** Edge comes from target to this node. */
        INCOMING,

        /** Edge is traversed both ways. */
        BOTH
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NESTED ANNOTATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Maps entity field to graph edge property.
     *
     * <p><strong>Status: RESERVED</strong> — {@link GraphEdge#propertyMappings()} is not
     * extracted, so the emitted graph-sync writer carries no property mapping.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({})
    @interface PropertyMapping {
        /**
         * Entity field name.
         * @return the field name
         */
        String field();

        /**
         * Edge property name.
         * @return the property name
         */
        String property();

        /**
         * Deprecated, no effect: transforms the value with a SpEL expression.
         *
         * @return the transform expression
         * @deprecated since 0.13.0, for removal in 1.0.0. Map the field as it is, or derive
         *         the value in the entity. No generator and no kernel component evaluates a
         *         SpEL expression. See {@code MIGRATION.md}.
         */
        @Deprecated(since = "0.13.0", forRemoval = true)
        String transform() default "";
    }

    /**
     * Static property with fixed value on graph edge.
     *
     * <p><strong>Status: RESERVED</strong> — {@link GraphEdge#staticProperties()} is not
     * extracted.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({})
    @interface StaticProperty {
        /** Property name.
         * @return the name */
        String name();

        /** Property value.
         * @return the value */
        String value();
    }

    /**
     * Computed property using SpEL expression.
     *
     * @deprecated since 0.13.0, for removal in 1.0.0, with {@link GraphEdge#computedProperties()}.
     *         No replacement: no generator and no kernel component evaluates a SpEL expression.
     *         See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    @Retention(RetentionPolicy.SOURCE)
    @Target({})
    @interface ComputedProperty {
        /** Property name.
         * @return the name */
        String name();

        /** SpEL expression to compute value.
         * @return the expression */
        String expression();
    }
}
