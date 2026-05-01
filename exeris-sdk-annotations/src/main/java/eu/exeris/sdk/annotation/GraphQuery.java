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
 * @author Exeris platform Team
 * @version 1.0.0
 * @since 1.0.0
 * @see Graph
 * @see GraphProperty
 * @see GraphEdge
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SuppressWarnings({"PMD.ExcessivePublicCount", "PMD.TooManyMethods"})
public @interface GraphQuery {

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // QUERY TYPE
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // TRAVERSAL CONFIGURATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // SIMILARITY CONFIGURATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // PATH CONFIGURATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // RECOMMENDATION CONFIGURATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // AGGREGATION CONFIGURATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // PATTERN MATCHING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // CUSTOM QUERY
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // FILTERING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // ORDERING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // RESULT CONFIGURATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // CACHING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // PERFORMANCE
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // MONITORING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // GRAPHQL
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // ENUMS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

    enum Direction {
        OUTGOING,
        INCOMING,
        BOTH
    }

    enum SimilarityAlgorithm {
        AUTO,
        COSINE,
        EUCLIDEAN,
        JACCARD,
        PEARSON,
        OVERLAP,
        LEVENSHTEIN,
        JARO_WINKLER,
        CUSTOM
    }

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

    enum AggregationType {
        COUNT,
        SUM,
        AVG,
        MIN,
        MAX,
        COLLECT,
        DISTINCT_COUNT,
        PERCENTILE,
        STDDEV,
        VARIANCE
    }

    enum OrderDirection {
        ASC,
        DESC
    }

    enum LogLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        OFF
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // NESTED ANNOTATIONS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @interface GraphqlArg {
        String name();
        String type();
        String defaultValue() default "";
        boolean required() default false;
        String description() default "";
    }
}


