package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Defines a graph query method on a domain entity.
 *
 * <p>Generates methods that execute graph traversals, similarity searches,
 * path finding, and other graph algorithms.
 *
 * <h2>Similar Items Query:</h2>
 * <pre>{@code
 * @GraphQuery(
 *     type = QueryType.SIMILAR,
 *     maxResults = 5,
 *     minSimilarity = 0.7
 * )
 * public List<Product> findSimilarProducts() {
 *     return List.of(); // Implementation generated
 * }
 * }</pre>
 *
 * <h2>Connected Nodes Query:</h2>
 * <pre>{@code
 * @GraphQuery(
 *     type = QueryType.CONNECTED,
 *     edgeTypes = {"PURCHASED", "VIEWED"},
 *     nodeClasses = {"Product"},
 *     maxDepth = 2
 * )
 * public List<Product> findRelatedProducts() {
 *     return List.of();
 * }
 * }</pre>
 *
 * <h2>Shortest Path Query:</h2>
 * <pre>{@code
 * @GraphQuery(
 *     type = QueryType.PATH,
 *     edgeTypes = {"FRIEND_OF", "FOLLOWS"},
 *     maxDepth = 6,
 *     pathAlgorithm = PathAlgorithm.DIJKSTRA
 * )
 * public GraphPath findPathTo(UUID targetUserId) {
 *     return null;
 * }
 * }</pre>
 *
 * <h2>Recommendations Query:</h2>
 * <pre>{@code
 * @GraphQuery(
 *     type = QueryType.RECOMMEND,
 *     recommendationAlgorithm = RecommendationAlgorithm.COLLABORATIVE_FILTERING,
 *     maxResults = 10,
 *     minRecommendationScore = 0.5
 * )
 * public List<ProductRecommendation> getRecommendations() {
 *     return List.of();
 * }
 * }</pre>
 *
 * <h2>Custom Cypher Query:</h2>
 * <pre>{@code
 * @GraphQuery(
 *     type = QueryType.CUSTOM,
 *     cypher = """
 *         MATCH (u:User {id: $userId})-[:PURCHASED]->(p:Product)
 *         <-[:PURCHASED]-(other:User)-[:PURCHASED]->(rec:Product)
 *         WHERE NOT (u)-[:PURCHASED]->(rec)
 *         RETURN rec, count(*) as score
 *         ORDER BY score DESC
 *         LIMIT $limit
 *         """
 * )
 * public List<Product> getCollaborativeRecommendations(
 *     @QueryParam("userId") UUID userId,
 *     @QueryParam("limit") int limit
 * ) {
 *     return List.of();
 * }
 * }</pre>
 *
 *
 * <p><strong>Status: RESERVED</strong> — not extracted by the {@code exeris-tooling}
 * processor — {@code GraphMetadata.queries} is passed as an empty list. There is also no
 * GraphQL emitter anywhere in the toolchain, so both hops are missing rather than one.
 * @author Exeris SDK Team
 * @version 0.1.0
 * @since 0.1.0
 * @see Graph
 * @see GraphProperty
 * @see GraphEdge
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
@Documented
@SuppressWarnings({"PMD.ExcessivePublicCount", "PMD.TooManyMethods"})
public @interface GraphQuery {

    // ═══════════════════════════════════════════════════════════════════════════
    // QUERY TYPE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Type of graph query.
     *
     * @return query type
     */
    QueryType type();

    /**
     * Query name (defaults to method name).
     *
     * @return query name
     */
    String name() default "";

    /**
     * Query description.
     *
     * @return description
     */
    String description() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // TRAVERSAL CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Edge types to traverse.
     * <p>Empty = all edge types.
     *
     * @return edge types
     */
    String[] edgeTypes() default {};

    /**
     * Edge types to exclude.
     *
     * @return excluded edge types
     */
    String[] excludeEdgeTypes() default {};

    /**
     * Node classes/labels to include in results.
     *
     * @return node classes
     */
    String[] nodeClasses() default {};

    /**
     * Node classes to exclude.
     *
     * @return excluded node classes
     */
    String[] excludeNodeClasses() default {};

    /**
     * Edge direction for traversal.
     *
     * @return direction
     */
    Direction direction() default Direction.BOTH;

    /**
     * Maximum traversal depth.
     *
     * @return max depth
     */
    int maxDepth() default 3;

    /**
     * Minimum depth (for path queries).
     *
     * @return min depth
     */
    int minDepth() default 1;

    /**
     * Maximum results to return.
     *
     * @return max results
     */
    int maxResults() default 10;

    /**
     * Skip first N results (pagination).
     *
     * @return skip count
     */
    int skip() default 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // SIMILARITY CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Minimum similarity score (0.0 - 1.0).
     * <p>Only for SIMILAR query type.
     *
     * @return min similarity
     */
    double minSimilarity() default 0.5;

    /**
     * Fields to use for similarity calculation.
     * <p>Overrides @GraphProperty(similarity=true) settings.
     *
     * @return similarity fields
     */
    String[] similarityFields() default {};

    /**
     * Similarity algorithm.
     *
     * @return algorithm
     */
    SimilarityAlgorithm similarityAlgorithm() default SimilarityAlgorithm.AUTO;

    /**
     * Include similarity score in results.
     *
     * @return true to include score
     */
    boolean includeSimilarityScore() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // PATH CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Path finding algorithm.
     * <p>Only for PATH query type.
     *
     * @return algorithm
     */
    PathAlgorithm pathAlgorithm() default PathAlgorithm.DIJKSTRA;

    /**
     * Weight property for path calculations.
     *
     * @return weight property
     */
    String pathWeightProperty() default "weight";

    /**
     * Default weight for edges without weight property.
     *
     * @return default weight
     */
    double pathDefaultWeight() default 1.0;

    /**
     * Return all shortest paths (not just one).
     *
     * @return true for all paths
     */
    boolean allShortestPaths() default false;

    /**
     * Include path details (edges, intermediate nodes).
     *
     * @return true to include
     */
    boolean includePathDetails() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // RECOMMENDATION CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Recommendation algorithm.
     * <p>Only for RECOMMEND query type.
     *
     * @return algorithm
     */
    RecommendationAlgorithm recommendationAlgorithm() default RecommendationAlgorithm.COLLABORATIVE_FILTERING;

    /**
     * Minimum recommendation score.
     *
     * @return min score
     */
    double minRecommendationScore() default 0.0;

    /**
     * Include recommendation explanation.
     *
     * @return true to include
     */
    boolean includeRecommendationReason() default false;

    /**
     * Items to exclude from recommendations (already owned, etc.).
     *
     * @return exclusion expression (SpEL)
     */
    String excludeFromRecommendations() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // AGGREGATION CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Aggregation function.
     * <p>Only for AGGREGATE query type.
     *
     * @return aggregation type
     */
    AggregationType aggregation() default AggregationType.COUNT;

    /**
     * Property to aggregate.
     *
     * @return property name
     */
    String aggregateProperty() default "";

    /**
     * Group by properties.
     *
     * @return group by properties
     */
    String[] groupBy() default {};

    /**
     * Having clause (SpEL expression).
     *
     * @return having expression
     */
    String having() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // PATTERN MATCHING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Pattern to match (Cypher pattern syntax).
     * <p>Only for PATTERN query type.
     *
     * @return pattern
     */
    String pattern() default "";

    /**
     * Pattern variables to return.
     *
     * @return variable names
     */
    String[] returnVariables() default {};

    // ═══════════════════════════════════════════════════════════════════════════
    // CUSTOM QUERY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Custom Cypher query.
     * <p>Only for CUSTOM query type.
     * <p>Use $paramName for parameters.
     *
     * @return Cypher query
     */
    String cypher() default "";

    /**
     * Custom Gremlin query.
     * <p>Alternative to Cypher.
     *
     * @return Gremlin query
     */
    String gremlin() default "";

    /**
     * Custom Apache AGE query.
     * <p>For PostgreSQL AGE extension.
     *
     * @return AGE query
     */
    String ageQuery() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // FILTERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Node filter expression (SpEL).
     * <p>Applied to all nodes in traversal.
     *
     * @return filter expression
     */
    String nodeFilter() default "";

    /**
     * Edge filter expression (SpEL).
     * <p>Applied to all edges in traversal.
     *
     * @return filter expression
     */
    String edgeFilter() default "";

    /**
     * Result filter expression (SpEL).
     * <p>Applied to final results.
     *
     * @return filter expression
     */
    String resultFilter() default "";

    /**
     * Include tenant filter.
     *
     * @return true to filter by tenant
     */
    boolean tenantFilter() default true;

    /**
     * Include soft-delete filter.
     *
     * @return true to exclude deleted
     */
    boolean excludeDeleted() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // ORDERING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Order by property.
     *
     * @return property name
     */
    String orderBy() default "";

    /**
     * Order direction.
     *
     * @return order direction
     */
    OrderDirection orderDirection() default OrderDirection.DESC;

    /**
     * Secondary order by.
     *
     * @return property name
     */
    String thenOrderBy() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // RESULT CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result type class.
     * <p>Override return type inference.
     *
     * @return result class
     */
    Class<?> resultType() default void.class;

    /**
     * Project specific properties.
     *
     * @return property names
     */
    String[] projection() default {};

    /**
     * Include node metadata (labels, id).
     *
     * @return true to include
     */
    boolean includeMetadata() default false;

    /**
     * Include edge information.
     *
     * @return true to include
     */
    boolean includeEdges() default false;

    /**
     * Transform result (SpEL expression).
     *
     * @return transform expression
     */
    String resultTransform() default "";

    /**
     * Result mapper class.
     *
     * @return mapper class
     */
    Class<?> resultMapper() default void.class;

    // ═══════════════════════════════════════════════════════════════════════════
    // CACHING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Cache query results.
     *
     * @return true to cache
     */
    boolean cached() default true;

    /**
     * Cache TTL.
     *
     * @return cache TTL
     */
    String cacheTtl() default "PT5M";

    /**
     * Cache key expression (SpEL).
     *
     * @return cache key expression
     */
    String cacheKey() default "";

    /**
     * Cache region.
     *
     * @return region name
     */
    String cacheRegion() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // PERFORMANCE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Query timeout.
     *
     * @return timeout
     */
    String timeout() default "PT30S";

    /**
     * Memory limit for query execution (MB).
     *
     * @return memory limit
     */
    int memoryLimit() default 0;

    /**
     * Use query planner hints.
     *
     * @return hints
     */
    String[] hints() default {};

    /**
     * Parallelize query execution.
     *
     * @return true to parallelize
     */
    boolean parallel() default false;

    // ═══════════════════════════════════════════════════════════════════════════
    // MONITORING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Log query execution.
     *
     * @return log level
     */
    LogLevel logLevel() default LogLevel.DEBUG;

    /**
     * Emit query metrics.
     *
     * @return true to emit
     */
    boolean emitMetrics() default true;

    /**
     * Alert on slow query.
     *
     * @return threshold
     */
    String slowQueryThreshold() default "PT5S";

    /**
     * Query tags for monitoring.
     *
     * @return tags
     */
    String[] tags() default {};

    // ═══════════════════════════════════════════════════════════════════════════
    // GRAPHQL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Expose as GraphQL field.
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
     * GraphQL field description.
     *
     * @return description
     */
    String graphqlDescription() default "";

    /**
     * GraphQL return type.
     *
     * @return type name
     */
    String graphqlReturnType() default "";

    /**
     * GraphQL field arguments.
     *
     * @return arguments
     */
    GraphqlArg[] graphqlArgs() default {};

    // ═══════════════════════════════════════════════════════════════════════════
    // ENUMS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * The shape of the traversal this query performs.
     */
    enum QueryType {
        /** Find similar nodes */
        SIMILAR,

        /** Find connected/related nodes */
        CONNECTED,

        /** Find shortest path between nodes */
        PATH,

        /** Aggregate data over graph */
        AGGREGATE,

        /** Pattern matching query */
        PATTERN,

        /** Get recommendations */
        RECOMMEND,

        /** Detect communities/clusters */
        COMMUNITY,

        /** Centrality calculations */
        CENTRALITY,

        /** Neighbors query */
        NEIGHBORS,

        /** Subgraph extraction */
        SUBGRAPH,

        /** Custom query (Cypher/Gremlin) */
        CUSTOM
    }

    /**
     * Which way an edge is followed during traversal.
     */
    enum Direction {
        /** Follow edges leaving the start node. */
        OUTGOING,
        /** Follow edges arriving at the start node. */
        INCOMING,
        /** Follow edges in either direction. */
        BOTH
    }

    /**
     * How similarity between two nodes is scored.
     */
    enum SimilarityAlgorithm {
        /** Let the provider choose, based on the property types involved. */
        AUTO,
        /** Cosine of the angle between two vectors; magnitude-insensitive. */
        COSINE,
        /** Straight-line distance between two vectors. */
        EUCLIDEAN,
        /** Overlap of two sets, relative to their union. */
        JACCARD,
        /** Linear correlation between two value series. */
        PEARSON,
        /** Overlap of two sets, relative to the smaller one. */
        OVERLAP,
        /** Edit distance between two strings. */
        LEVENSHTEIN,
        /** String similarity weighted towards a shared prefix. */
        JARO_WINKLER,
        /** A provider-supplied measure named elsewhere on the query. */
        CUSTOM
    }

    /**
     * How a path between two nodes is searched for.
     */
    enum PathAlgorithm {
        /** Dijkstra's shortest path */
        DIJKSTRA,

        /** A* algorithm */
        A_STAR,

        /** Breadth-first search */
        BFS,

        /** Depth-first search */
        DFS,

        /** Bellman-Ford (handles negative weights) */
        BELLMAN_FORD,

        /** All shortest paths */
        ALL_SHORTEST_PATHS,

        /** K shortest paths */
        K_SHORTEST_PATHS,

        /** Random walk */
        RANDOM_WALK
    }

    /**
     * How recommendations are derived from the graph.
     */
    enum RecommendationAlgorithm {
        /** Collaborative filtering */
        COLLABORATIVE_FILTERING,

        /** Content-based filtering */
        CONTENT_BASED,

        /** Hybrid approach */
        HYBRID,

        /** Graph-based (PageRank, etc.) */
        GRAPH_BASED,

        /** Matrix factorization */
        MATRIX_FACTORIZATION,

        /** Association rules */
        ASSOCIATION_RULES,

        /** Custom algorithm */
        CUSTOM
    }

    /**
     * How matched values are reduced to a single result.
     */
    enum AggregationType {
        /** How many values matched. */
        COUNT,
        /** Their total. */
        SUM,
        /** Their mean. */
        AVG,
        /** The smallest. */
        MIN,
        /** The largest. */
        MAX,
        /** All of them, as a list. */
        COLLECT,
        /** How many distinct values matched. */
        DISTINCT_COUNT,
        /** A percentile of the distribution. */
        PERCENTILE,
        /** Their standard deviation. */
        STDDEV,
        /** Their variance. */
        VARIANCE
    }

    /**
     * Which way results are sorted.
     */
    enum OrderDirection {
        /** Smallest first. */
        ASC,
        /** Largest first. */
        DESC
    }

    /**
     * How much detail the generated code logs for this surface.
     */
    enum LogLevel {
        /** Finest detail; every step and its payload. */
        TRACE,
        /** Diagnostic detail useful while developing. */
        DEBUG,
        /** Normal progress worth recording. */
        INFO,
        /** Something unexpected that did not stop the work. */
        WARN,
        /** A failure. */
        ERROR,
        /** No logging at all. */
        OFF
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NESTED ANNOTATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * One argument of the GraphQL field this query is exposed as.
     *
     * <p>{@code type} is written in GraphQL's own type syntax rather than as a Java type —
     * the two vocabularies do not coincide, and this one is the schema's.
     *
     * <p><strong>Status: RESERVED</strong> — {@code @GraphQuery} is not extracted, so an
     * argument declared here describes a schema no emitter writes.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({})
    @interface GraphqlArg {
        /**
         * Argument name as it appears in the schema.
         *
         * @return the argument name
         */
        String name();

        /**
         * GraphQL type of the argument — e.g. {@code String}, {@code Int}, {@code ID} —
         * not a Java type.
         *
         * @return the GraphQL type
         */
        String type();

        /**
         * Value used when the caller omits the argument.
         *
         * @return the default value
         */
        String defaultValue() default "";

        /**
         * Whether the caller must supply the argument.
         *
         * @return true when the argument is required
         */
        boolean required() default false;

        /**
         * Human-readable description carried into the generated schema.
         *
         * @return the argument description
         */
        String description() default "";
    }
}


