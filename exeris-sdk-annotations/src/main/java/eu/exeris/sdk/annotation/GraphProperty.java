package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Configures a field as a graph node property.
 *
 * <p>Defines how a field is stored and indexed in the graph database,
 * and whether it participates in similarity calculations.
 *
 * <h2>Basic Usage:</h2>
 * <pre>{@code
 * @GraphProperty(indexed = true)
 * private String name;
 *
 * @GraphProperty(similarity = true, similarityWeight = 0.3)
 * private String description;
 *
 * @GraphProperty(indexed = true, fullText = true)
 * private String content;
 * }</pre>
 *
 * <h2>With Custom Name and Type:</h2>
 * <pre>{@code
 * @GraphProperty(
 *     name = "amt",
 *     graphType = GraphType.DOUBLE,
 *     indexed = true
 * )
 * private BigDecimal amount;
 * }</pre>
 *
 * @author Exeris SDK Team
 * @version 0.1.0
 * @since 0.1.0
 * @see Graph
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface GraphProperty {

    // ═══════════════════════════════════════════════════════════════════════════
    // IDENTITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Property name in graph database.
     * <p>Defaults to field name.
     *
     * @return property name
     */
    String name() default "";

    /**
     * Property description.
     *
     * @return description
     */
    String description() default "";

    // ═══════════════════════════════════════════════════════════════════════════
    // TYPE CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Graph property type.
     * <p>Defaults to AUTO (inferred from Java type).
     *
     * @return graph type
     */
    GraphType graphType() default GraphType.AUTO;

    /**
     * Is property nullable in graph.
     *
     * @return true if nullable
     */
    boolean nullable() default true;

    /**
     * Default value if null.
     *
     * @return default value as string
     */
    String defaultValue() default "";

    /**
     * Array/list property.
     *
     * @return true if array
     */
    boolean array() default false;

    // ═══════════════════════════════════════════════════════════════════════════
    // INDEXING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create index on this property.
     *
     * @return true to index
     */
    boolean indexed() default false;

    /**
     * Index type.
     *
     * @return index type
     */
    IndexType indexType() default IndexType.BTREE;

    /**
     * Unique constraint on this property.
     *
     * @return true for unique
     */
    boolean unique() default false;

    /**
     * Include in composite index.
     * <p>Index name for composite indexes.
     *
     * @return composite index name
     */
    String compositeIndex() default "";

    /**
     * Composite index order.
     *
     * @return order in composite
     */
    int compositeIndexOrder() default 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // FULL-TEXT SEARCH
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Include in full-text search index.
     *
     * @return true for full-text
     */
    boolean fullText() default false;

    /**
     * Full-text index name.
     *
     * @return index name
     */
    String fullTextIndex() default "";

    /**
     * Full-text analyzer.
     *
     * @return analyzer name
     */
    String fullTextAnalyzer() default "standard";

    /**
     * Full-text search weight/boost.
     *
     * @return boost factor
     */
    double fullTextBoost() default 1.0;

    // ═══════════════════════════════════════════════════════════════════════════
    // SIMILARITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Include in similarity calculations.
     *
     * @return true for similarity
     */
    boolean similarity() default false;

    /**
     * Weight in similarity calculation (0.0 - 1.0).
     *
     * @return similarity weight
     */
    double similarityWeight() default 1.0;

    /**
     * Similarity calculation method for this property.
     *
     * @return similarity method
     */
    SimilarityMethod similarityMethod() default SimilarityMethod.AUTO;

    /**
     * Tokenize for similarity (text fields).
     *
     * @return true to tokenize
     */
    boolean tokenize() default true;

    /**
     * Tokenizer for similarity.
     *
     * @return tokenizer name
     */
    String tokenizer() default "standard";

    /**
     * Normalize values for similarity.
     *
     * @return true to normalize
     */
    boolean normalize() default true;

    // ═══════════════════════════════════════════════════════════════════════════
    // VECTOR EMBEDDINGS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate vector embedding for this property.
     *
     * @return true to generate
     */
    boolean vectorEmbedding() default false;

    /**
     * Embedding model/service.
     *
     * @return embedding service
     */
    String embeddingService() default "";

    /**
     * Vector property name in graph.
     *
     * @return vector property name
     */
    String vectorProperty() default "";

    /**
     * Vector dimensions.
     *
     * @return dimensions
     */
    int vectorDimensions() default 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSFORMATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Transform value before storing in graph (SpEL).
     *
     * @return transform expression
     */
    String transform() default "";

    /**
     * Transformer class.
     *
     * @return transformer class
     */
    Class<?> transformer() default void.class;

    /**
     * Encrypt value in graph.
     *
     * @return true to encrypt
     */
    boolean encrypted() default false;

    /**
     * Hash value before storing.
     *
     * @return true to hash
     */
    boolean hashed() default false;

    /**
     * Mask value in queries/logs.
     *
     * @return true to mask
     */
    boolean masked() default false;

    // ═══════════════════════════════════════════════════════════════════════════
    // SYNC CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sync this property to graph.
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
     * Exclude from initial sync (dynamic property).
     *
     * @return true to exclude
     */
    boolean excludeFromInitialSync() default false;

    // ═══════════════════════════════════════════════════════════════════════════
    // ENUMS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Graph property data types.
     */
    enum GraphType {
        /** Auto-detect from Java type */
        AUTO,
        /** String type */
        STRING,
        /** Integer type */
        INTEGER,
        /** Long type */
        LONG,
        /** Double type */
        DOUBLE,
        /** Float type */
        FLOAT,
        /** Boolean type */
        BOOLEAN,
        /** Date type */
        DATE,
        /** DateTime type */
        DATETIME,
        /** Timestamp type */
        TIMESTAMP,
        /** JSON type */
        JSON,
        /** Array type */
        ARRAY,
        /** Map type */
        MAP,
        /** Geographic point type */
        POINT,
        /** Vector type (for embeddings) */
        VECTOR
    }

    /**
     * Index types for graph properties.
     */
    enum IndexType {
        /** B-tree index (default) */
        BTREE,

        /** Hash index */
        HASH,

        /** Full-text index */
        FULLTEXT,

        /** Vector/similarity index */
        VECTOR,

        /** Spatial index */
        SPATIAL,

        /** Range index */
        RANGE
    }

    /**
     * How similarity between two vector values is measured.
     */
    enum SimilarityMethod {
        /** Auto-detect based on type */
        AUTO,

        /** Exact match */
        EXACT,

        /** Fuzzy string matching */
        FUZZY,

        /** Token/word overlap */
        TOKEN,

        /** Numeric difference */
        NUMERIC,

        /** Date/time proximity */
        TEMPORAL,

        /** Vector cosine similarity */
        VECTOR,

        /** Custom implementation */
        CUSTOM
    }
}


