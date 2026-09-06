package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Marks a method parameter as a graph query parameter.
 *
 * <p>Used with @GraphQuery to bind method parameters to query variables.
 *
 * <h2>Usage:</h2>
 * {@snippet lang="java" :
 * @GraphQuery(
 *     type = QueryType.CUSTOM,
 *     cypher = "MATCH (n:Product) WHERE n.price >= $minPrice AND n.price <= $maxPrice RETURN n LIMIT $limit"
 * )
 * public List<Product> findByPriceRange(
 *     @QueryParam("minPrice") BigDecimal minPrice,
 *     @QueryParam("maxPrice") BigDecimal maxPrice,
 *     @QueryParam(value = "limit", defaultValue = "10") int limit
 * ) {
 *     return List.of();
 * }
 * }
 *
 *
 * <p><strong>Status: RESERVED</strong> — action parameters are extracted through {@link ActionParam @ActionParam} only. A parameter carrying just this annotation reaches {@code ActionMetadata} as if it were unannotated.
 * @since 0.1
 * @see GraphQuery
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface QueryParam {

    /**
     * Parameter name in query.
     * <p>Used as $name in Cypher or as binding in Gremlin.
     *
     * @return parameter name
     */
    String value();

    /**
     * Default value if parameter is null.
     *
     * @return default value as string
     */
    String defaultValue() default "";

    /**
     * Whether parameter is required.
     *
     * @return true if required
     */
    boolean required() default true;

    /**
     * Parameter description.
     *
     * @return description
     */
    String description() default "";

    /**
     * Transform parameter before binding (SpEL).
     *
     * @return transform expression
     */
    String transform() default "";

    /**
     * Validate parameter value (SpEL returning boolean).
     *
     * @return validation expression
     */
    String validation() default "";

    /**
     * Validation error message.
     *
     * @return error message
     */
    String validationMessage() default "";

    /**
     * Parameter type hint for conversion.
     *
     * @return type class
     */
    Class<?> type() default void.class;

    /**
     * For collection parameters - element type.
     *
     * @return element type
     */
    Class<?> elementType() default void.class;

    /**
     * Sanitize input (prevent injection).
     *
     * @return true to sanitize
     */
    boolean sanitize() default true;
}


