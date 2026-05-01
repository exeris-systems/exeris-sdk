package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Defines a graph edge (relationship) from this entity to another.
 *
 * <p>Creates edges in the graph database representing relationships
 * between entities. Supports weighted edges, bidirectional relationships,
 * and edge properties.
 *
 * <h2>Basic Usage:</h2>
 * <pre>{@code
 * @GraphEdge(type = "BELONGS_TO", target = Category.class)
 * private Category category;
 *
 * @GraphEdge(
 *     type = "PURCHASED_BY",
 *     target = Customer.class,
 *     direction = Direction.INCOMING
 * )
 * private Customer customer;
 * }</pre>
 *
 * <h2>Weighted Edge:</h2>
 * <pre>{@code
 * @GraphEdge(
 *     type = "RATED",
 *     target = Product.class,
 *     weighted = true,
 *     weightField = "rating",
 *     properties = {"rating", "reviewDate"}
 * )
 * private Product ratedProduct;
 * }</pre>
 *
 * <h2>Bidirectional:</h2>
 * <pre>{@code
 * @GraphEdge(
 *     type = "FRIEND_OF",
 *     target = User.class,
 *     bidirectional = true
 * )
 * private List<User> friends;
 * }</pre>
 *
 * @author Exeris platform Team
 * @version 1.0.0
 * @since 1.0.0
 * @see Graph
 * @see GraphProperty
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(GraphEdges.class)
@SuppressWarnings({"PMD.ExcessivePublicCount", "PMD.TooManyMethods"})
public @interface GraphEdge {

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // EDGE IDENTITY
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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
     * Edge description.
     *
     * @return description
     */
    String description() default "";

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // DIRECTION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Edge direction.
     *
     * @return direction
     */
    Direction direction() default Direction.OUTGOING;

    /**
     * Create bidirectional edge (two edges, one each way).
     *
     * @return true for bidirectional
     */
    boolean bidirectional() default false;

    /**
     * Inverse edge type (for bidirectional).
     * <p>Type of the reverse edge. Defaults to same as forward type.
     *
     * @return inverse type
     */
    String inverseType() default "";

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // EDGE PROPERTIES
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Entity fields to copy as edge properties.
     *
     * @return property field names
     */
    String[] properties() default {};

    /**
     * Edge property mappings (entity field to edge property).
     *
     * @return property mappings
     */
    PropertyMapping[] propertyMappings() default {};

    /**
     * Static edge properties.
     *
     * @return static properties
     */
    StaticProperty[] staticProperties() default {};

    /**
     * Computed edge properties (SpEL expressions).
     *
     * @return computed properties
     */
    ComputedProperty[] computedProperties() default {};

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // WEIGHT
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Edge is weighted.
     *
     * @return true if weighted
     */
    boolean weighted() default false;

    /**
     * Field containing edge weight.
     *
     * @return weight field name
     */
    String weightField() default "";

    /**
     * Weight property name in graph.
     *
     * @return weight property name
     */
    String weightProperty() default "weight";

    /**
     * Static weight value (if not from field).
     *
     * @return static weight
     */
    double staticWeight() default 1.0;

    /**
     * Weight expression (SpEL).
     *
     * @return weight expression
     */
    String weightExpression() default "";

    /**
     * Normalize weight values.
     *
     * @return true to normalize
     */
    boolean normalizeWeight() default false;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // CONSTRAINTS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Maximum edges of this type from source node.
     * <p>0 = unlimited.
     *
     * @return max edges
     */
    int maxEdges() default 0;

    /**
     * Minimum edges required.
     *
     * @return min edges
     */
    int minEdges() default 0;

    /**
     * Unique edge (only one per source-target pair).
     *
     * @return true for unique
     */
    boolean unique() default false;

    /**
     * Update existing edge instead of creating new.
     *
     * @return true to upsert
     */
    boolean upsert() default true;

    /**
     * Condition for creating edge (SpEL).
     *
     * @return condition expression
     */
    String condition() default "";

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // CASCADE
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Cascade delete (delete edge when source deleted).
     *
     * @return true to cascade
     */
    boolean cascadeDelete() default true;

    /**
     * Cascade delete target node (only if no other edges).
     *
     * @return true to cascade delete target
     */
    boolean cascadeDeleteOrphanTarget() default false;

    /**
     * Update edge when source updated.
     *
     * @return true to update
     */
    boolean cascadeUpdate() default true;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // INDEXING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Create index on this edge type.
     *
     * @return true to index
     */
    boolean indexed() default false;

    /**
     * Index edge properties.
     *
     * @return properties to index
     */
    String[] indexedProperties() default {};

    /**
     * Composite edge index name.
     *
     * @return index name
     */
    String compositeIndex() default "";

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // TEMPORAL
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Track edge timestamps.
     *
     * @return true to track
     */
    boolean temporal() default false;

    /**
     * Created at property name.
     *
     * @return property name
     */
    String createdAtProperty() default "createdAt";

    /**
     * Updated at property name.
     *
     * @return property name
     */
    String updatedAtProperty() default "updatedAt";

    /**
     * Edge valid from (temporal edge start).
     *
     * @return field name
     */
    String validFrom() default "";

    /**
     * Edge valid to (temporal edge end).
     *
     * @return field name
     */
    String validTo() default "";

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // SYNC CONFIGURATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Sync this edge to graph.
     *
     * @return true to sync
     */
    boolean sync() default true;

    /**
     * Sync only on specific events.
     *
     * @return event names
     */
    String[] syncOnEvents() default {};

    /**
     * Sync edge properties on update.
     *
     * @return true to sync properties
     */
    boolean syncProperties() default true;

    /**
     * Batch edge creation.
     *
     * @return true to batch
     */
    boolean batch() default true;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // TRAVERSAL
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Include in default traversals.
     *
     * @return true to include
     */
    boolean traversable() default true;

    /**
     * Traversal cost/weight (for path algorithms).
     *
     * @return traversal cost
     */
    double traversalCost() default 1.0;

    /**
     * Traversal cost expression (SpEL).
     *
     * @return cost expression
     */
    String traversalCostExpression() default "";

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // GRAPHQL
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Expose in GraphQL schema.
     *
     * @return true to expose
     */
    boolean graphqlExpose() default true;

    /**
     * GraphQL field name.
     *
     * @return field name
     */
    String graphqlFieldName() default "";

    /**
     * GraphQL description.
     *
     * @return description
     */
    String graphqlDescription() default "";

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // ENUMS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Edge direction in the graph.
     */
    enum Direction {
        /** Edge goes from this node to target */
        OUTGOING,

        /** Edge comes from target to this node */
        INCOMING,

        /** Edge direction doesn't matter (query both ways) */
        BOTH
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // NESTED ANNOTATIONS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Maps entity field to graph edge property.
     */
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
         * Transform expression (SpEL).
         * @return the transform expression
         */
        String transform() default "";
    }

    /**
     * Static property with fixed value on graph edge.
     */
    @interface StaticProperty {
        /** Property name
         * @return the name */
        String name();

        /** Property value
         * @return the value */
        String value();
    }

    /**
     * Computed property using SpEL expression.
     */
    @interface ComputedProperty {
        /** Property name
         * @return the name */
        String name();

        /** SpEL expression to compute value
         * @return the expression */
        String expression();
    }
}

/**
 * Container for multiple @GraphEdge annotations.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface GraphEdges {
    GraphEdge[] value();
}


