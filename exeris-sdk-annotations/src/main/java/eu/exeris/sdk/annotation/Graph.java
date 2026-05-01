package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Enables graph database integration for a domain entity.
 *
 * <p>Synchronizes entity data to a graph database (PostgreSQL with Apache AGE,
 * Neo4j, etc.) enabling graph traversals, similarity searches, and recommendations.
 *
 * <h2>Basic Usage:</h2>
 * <pre>{@code
 * @ExerisDomain(module = "pfm")
 * @Graph(
 *     nodeClass = "Transaction",
 *     syncToGraph = true,
 *     syncType = SyncType.PROJECTION
 * )
 * public class Transaction extends TenantAwareAggregate {
 *
 *     @GraphProperty(indexed = true)
 *     private BigDecimal amount;
 *
 *     @GraphEdge(type = "BELONGS_TO", target = Category.class)
 *     private Category category;
 * }
 * }</pre>
 *
 * <h2>With Similarity Search:</h2>
 * <pre>{@code
 * @Graph(
 *     nodeClass = "Product",
 *     syncToGraph = true,
 *     enableSimilarity = true,
 *     similarityFields = {"name", "description", "category"},
 *     similarityThreshold = 0.7,
 *     similarityAlgorithm = SimilarityAlgorithm.COSINE
 * )
 * public class Product { }
 * }</pre>
 *
 * <h2>With GraphQL Extensions:</h2>
 * <pre>{@code
 * @Graph(
 *     nodeClass = "User",
 *     syncToGraph = true,
 *     graphqlExtensions = true,
 *     graphqlQueries = {
 *         @GraphqlQuery(name = "recommendations", type = QueryType.SIMILAR),
 *         @GraphqlQuery(name = "connections", type = QueryType.CONNECTED)
 *     }
 * )
 * public class User { }
 * }</pre>
 *
 * @author Exeris platform Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SuppressWarnings({"PMD.ExcessivePublicCount", "PMD.TooManyMethods"})
public @interface Graph {

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // NODE CONFIGURATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Graph node class/label name.
     * <p>Label used in graph database (e.g., Cypher: MATCH (n:Transaction)).
     * Defaults to entity class name.
     *
     * @return node class name
     */
    String nodeClass() default "";

    /**
     * Node type identifier.
     * <p>Used for node type discrimination in mixed graphs.
     *
     * @return node type
     */
    String nodeType() default "";

    /**
     * Additional node labels.
     * <p>Secondary labels for the node.
     *
     * @return additional labels
     */
    String[] additionalLabels() default {};

    /**
     * Node ID property name in graph.
     * <p>Defaults to "entityId".
     *
     * @return ID property name
     */
    String idProperty() default "entityId";

    /**
     * Source table for entity data.
     * <p>Used for trigger-based sync.
     *
     * @return source table name
     */
    String sourceTable() default "";

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // SYNC CONFIGURATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Enable synchronization to graph database.
     *
     * @return true to sync
     */
    boolean syncToGraph() default true;

    /**
     * Synchronization mechanism.
     *
     * @return sync type
     */
    SyncType syncType() default SyncType.PROJECTION;

    /**
     * Sync on specific events only.
     * <p>Empty = sync on all entity events.
     *
     * @return event names to sync on
     */
    String[] syncOnEvents() default {};

    /**
     * Exclude events from sync.
     *
     * @return events to exclude
     */
    String[] excludeFromSync() default {};

    /**
     * Sync condition expression (SpEL).
     * <p>Only sync if condition is true.
     *
     * @return condition expression
     */
    String syncCondition() default "";

    /**
     * Batch sync operations.
     *
     * @return true for batching
     */
    boolean batchSync() default true;

    /**
     * Batch size for sync operations.
     *
     * @return batch size
     */
    int syncBatchSize() default 100;

    /**
     * Sync timeout per operation.
     *
     * @return sync timeout
     */
    String syncTimeout() default "PT10S";

    /**
     * Retry failed sync operations.
     *
     * @return max retries
     */
    int syncRetries() default 3;

    /**
     * Delay between sync retries.
     *
     * @return retry delay
     */
    String syncRetryDelay() default "PT1S";

    /**
     * Dead letter queue for failed syncs.
     *
     * @return DLQ name
     */
    String syncDlq() default "";

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // GRAPH DATABASE CONFIGURATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Graph database type.
     *
     * @return database type
     */
    GraphDatabase database() default GraphDatabase.APACHE_AGE;

    /**
     * Graph name (for Apache AGE).
     *
     * @return graph name
     */
    String graphName() default "corelio_graph";

    /**
     * Schema/namespace in graph database.
     *
     * @return schema name
     */
    String graphSchema() default "";

    /**
     * DataSource name for graph database.
     * <p>If different from main datasource.
     *
     * @return datasource name
     */
    String graphDataSource() default "";

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // SIMILARITY CONFIGURATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Enable similarity search for this entity.
     *
     * @return true to enable
     */
    boolean enableSimilarity() default false;

    /**
     * Fields used for similarity calculation.
     * <p>Must be marked with @GraphProperty(similarity = true).
     *
     * @return similarity field names
     */
    String[] similarityFields() default {};

    /**
     * Minimum similarity threshold (0.0 - 1.0).
     *
     * @return similarity threshold
     */
    double similarityThreshold() default 0.7;

    /**
     * Similarity algorithm.
     *
     * @return algorithm
     */
    SimilarityAlgorithm similarityAlgorithm() default SimilarityAlgorithm.COSINE;

    /**
     * Maximum similar items to return.
     *
     * @return max results
     */
    int maxSimilarResults() default 10;

    /**
     * Create SIMILAR_TO edges between similar nodes.
     *
     * @return true to create edges
     */
    boolean createSimilarityEdges() default false;

    /**
     * Edge type for similarity relationships.
     *
     * @return edge type
     */
    String similarityEdgeType() default "SIMILAR_TO";

    /**
     * Recalculate similarity on update.
     *
     * @return true to recalculate
     */
    boolean recalculateSimilarityOnUpdate() default true;

    /**
     * Similarity calculation schedule (cron).
     * <p>For batch recalculation.
     *
     * @return cron expression
     */
    String similaritySchedule() default "";

    /**
     * Use vector embeddings for similarity.
     *
     * @return true to use vectors
     */
    boolean useVectorEmbeddings() default false;

    /**
     * Embedding model/service.
     *
     * @return embedding service name
     */
    String embeddingService() default "";

    /**
     * Vector dimensions.
     *
     * @return dimensions
     */
    int vectorDimensions() default 384;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // INDEXING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Create indexes for node properties.
     *
     * @return true to create indexes
     */
    boolean createIndexes() default true;

    /**
     * Properties to index (in addition to @GraphProperty(indexed=true)).
     *
     * @return property names
     */
    String[] indexedProperties() default {};

    /**
     * Create full-text search index.
     *
     * @return true to create
     */
    boolean fullTextIndex() default false;

    /**
     * Properties for full-text index.
     *
     * @return property names
     */
    String[] fullTextProperties() default {};

    /**
     * Full-text index analyzer.
     *
     * @return analyzer name
     */
    String fullTextAnalyzer() default "standard";

    /**
     * Create unique constraint on ID.
     *
     * @return true to create
     */
    boolean uniqueConstraint() default true;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // TRAVERSAL CONFIGURATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Default max depth for traversals.
     *
     * @return max depth
     */
    int defaultMaxDepth() default 5;

    /**
     * Default max results for queries.
     *
     * @return max results
     */
    int defaultMaxResults() default 100;

    /**
     * Edge types to include in traversals.
     * <p>Empty = all edge types.
     *
     * @return edge types
     */
    String[] traversalEdgeTypes() default {};

    /**
     * Edge types to exclude from traversals.
     *
     * @return excluded edge types
     */
    String[] excludeEdgeTypes() default {};

    /**
     * Node labels to include in traversals.
     *
     * @return node labels
     */
    String[] traversalNodeLabels() default {};

    /**
     * Node labels to exclude from traversals.
     *
     * @return excluded labels
     */
    String[] excludeNodeLabels() default {};

    /**
     * Include tenant filter in traversals.
     *
     * @return true to filter by tenant
     */
    boolean tenantFilterInTraversals() default true;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // GRAPHQL EXTENSIONS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Generate GraphQL extensions for graph queries.
     *
     * @return true to generate
     */
    boolean graphqlExtensions() default true;

    /**
     * GraphQL queries to generate.
     *
     * @return query definitions
     */
    GraphqlQuery[] graphqlQueries() default {};

    /**
     * GraphQL type name for graph results.
     *
     * @return type name
     */
    String graphqlTypeName() default "";

    /**
     * Include graph fields in main entity type.
     *
     * @return true to include
     */
    boolean includeInEntityType() default true;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // CACHING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Cache graph query results.
     *
     * @return true to cache
     */
    boolean cacheQueries() default true;

    /**
     * Cache TTL.
     *
     * @return cache TTL
     */
    String cacheTtl() default "PT5M";

    /**
     * Cache region name.
     *
     * @return region name
     */
    String cacheRegion() default "";

    /**
     * Invalidate cache on entity update.
     *
     * @return true to invalidate
     */
    boolean invalidateCacheOnUpdate() default true;

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // MONITORING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Emit metrics for graph operations.
     *
     * @return true to emit
     */
    boolean emitMetrics() default true;

    /**
     * Log graph queries.
     *
     * @return log level
     */
    LogLevel queryLogLevel() default LogLevel.DEBUG;

    /**
     * Alert on sync failures.
     *
     * @return true to alert
     */
    boolean alertOnSyncFailure() default false;

    /**
     * Query timeout alert threshold.
     *
     * @return threshold
     */
    String queryAlertThreshold() default "PT5S";

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // ENUMS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Synchronization type for SQL-to-Graph projection.
     */
    enum SyncType {
        /** SQL trigger-based sync (immediate) */
        TRIGGER,

        /** Event projection-based sync (eventual) */
        PROJECTION,

        /** CDC-based sync (Debezium, etc.) */
        CDC,

        /** Manual sync via API */
        MANUAL,

        /** Scheduled batch sync */
        SCHEDULED
    }

    /**
     * Supported graph database backends.
     */
    enum GraphDatabase {
        /** PostgreSQL with Apache AGE extension */
        APACHE_AGE,

        /** Neo4j */
        NEO4J,

        /** Amazon Neptune */
        NEPTUNE,

        /** JanusGraph */
        JANUSGRAPH,

        /** TigerGraph */
        TIGERGRAPH,

        /** ArangoDB */
        ARANGODB,

        /** Custom implementation */
        CUSTOM
    }

    /**
     * Similarity algorithms for graph queries.
     */
    enum SimilarityAlgorithm {
        /** Cosine similarity */
        COSINE,

        /** Euclidean distance */
        EUCLIDEAN,

        /** Jaccard similarity */
        JACCARD,

        /** Pearson correlation */
        PEARSON,

        /** Custom algorithm */
        CUSTOM
    }

    /**
     * Log levels for graph operations.
     */
    enum LogLevel {
        /** Trace-level logging (most verbose) */
        TRACE,
        /** Debug-level logging */
        DEBUG,
        /** Info-level logging */
        INFO,
        /** Warning-level logging */
        WARN,
        /** Error-level logging */
        ERROR,
        /** Logging disabled */
        OFF
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // NESTED ANNOTATIONS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * GraphQL query definition for graph data.
     */
    @interface GraphqlQuery {
        /**
         * Query field name.
         * @return the query field name
         */
        String name();

        /**
         * Query type.
         * @return the query type
         */
        QueryType type();

        /**
         * Return type (GraphQL).
         * @return the GraphQL return type
         */
        String returnType() default "";

        /**
         * Query arguments.
         * @return the query arguments
         */
        QueryArg[] args() default {};

        /**
         * Description.
         * @return the description
         */
        String description() default "";

        /**
         * Max results override.
         * @return max results
         */
        int maxResults() default 0;

        /**
         * Max depth override.
         * @return max depth
         */
        int maxDepth() default 0;

        /**
         * Edge types filter.
         * @return edge types
         */
        String[] edgeTypes() default {};

        /**
         * Node labels filter.
         * @return node labels
         */
        String[] nodeLabels() default {};

        /**
         * Types of graph queries.
         */
        enum QueryType {
            /** Find similar nodes */
            SIMILAR,

            /** Find connected nodes */
            CONNECTED,

            /** Find shortest path */
            PATH,

            /** Aggregate over graph */
            AGGREGATE,

            /** Pattern matching */
            PATTERN,

            /** Recommendations */
            RECOMMEND,

            /** Community detection */
            COMMUNITY,

            /** Custom Cypher/Gremlin */
            CUSTOM
        }
    }

    /**
     * GraphQL query argument definition.
     */
    @interface QueryArg {
        /** Argument name
         * @return the name */
        String name();

        /** Argument type
         * @return the type */
        String type();

        /** Default value
         * @return the default value */
        String defaultValue() default "";

        /** Whether argument is required
         * @return true if required */
        boolean required() default false;

        /** Argument description
         * @return the description */
        String description() default "";
    }
}


