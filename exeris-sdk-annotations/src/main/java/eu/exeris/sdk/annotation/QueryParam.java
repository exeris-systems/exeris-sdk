package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Binds an {@link Action @Action} method parameter from the request's query string rather than
 * from the request body.
 *
 * <p>The annotation makes the parameter an action parameter on its own; {@link ActionParam
 * @ActionParam} is not required beside it. When both are present, {@code @ActionParam} owns the
 * parameter's name and label and is the nearer declaration for the attributes both declare —
 * {@link #required()}, {@link #description()} and {@link #defaultValue()} — and this annotation
 * supplies only what {@code @ActionParam} leaves undeclared.
 *
 * <h2>Usage:</h2>
 * {@snippet lang="java" :
 * @Action(name = "search", label = "Search orders", httpMethod = "GET")
 * public List<Order> search(
 *     @QueryParam("q") String term,                                      // ?q=...
 *     @QueryParam(value = "limit", required = false, defaultValue = "20") int limit
 * ) {
 *     return List.of();
 * }
 * }
 *
 * <p><strong>Status: PARTIAL</strong> — the {@code -io} reader carries it in
 * {@code ActionParamMetadata}: {@code bindingSource = QUERY}, and {@link #value()} as
 * {@code queryKey} when it differs from the parameter name. The {@code exeris-tooling} processor
 * does not extract it yet, so no generated handler reads the query string from it. On a method
 * that is not an {@code @Action}, a {@link GraphQuery @GraphQuery} method included, it is read by
 * nobody.
 *
 * @since 0.1
 * @see ActionParam
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface QueryParam {

    /**
     * The query-string key the parameter is read under.
     *
     * <p>Carried as {@code ActionParamMetadata.queryKey} when it differs from the parameter's
     * name; a key equal to the name, or blank, is carried as absent, and the name is the key.
     *
     * @return the query-string key
     */
    String value();

    /**
     * The value bound when the query string carries no value for the key, as source text.
     * Carried as {@code ActionParamMetadata.defaultValue} unless {@code @ActionParam} declares
     * one; blank means none.
     *
     * @return the default value
     */
    String defaultValue() default "";

    /**
     * Whether the query string must carry the key. Carried as
     * {@code ActionParamMetadata.required} unless {@code @ActionParam} declares it.
     *
     * @return {@code true} if the key is required
     */
    boolean required() default true;

    /**
     * Prose for generated documentation. Carried as {@code ActionParamMetadata.description}
     * unless {@code @ActionParam} declares one; blank means none.
     *
     * @return the description
     */
    String description() default "";

    /**
     * A SpEL expression applied to the value before binding.
     *
     * @return transform expression
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. A generated handler
     *         runs on the Spring-free kernel tier, which evaluates no SpEL, so the expression
     *         could not be honoured. Convert the value in the action method. No reader carries
     *         it. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String transform() default "";

    /**
     * A SpEL predicate the value must satisfy.
     *
     * @return validation expression
     * @deprecated since 0.13.0, for removal in 1.0.0. The kernel tier evaluates no SpEL, so the
     *         predicate could not be honoured. Declare bounds with
     *         {@code @ActionParam(validation = @Validation(...))}, whose bounds match the
     *         components {@code ActionParamMetadata} declares for them, and check anything
     *         further in the action method. No reader carries it. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String validation() default "";

    /**
     * The message reported when {@link #validation()} fails.
     *
     * @return error message
     * @deprecated since 0.13.0, for removal in 1.0.0, together with {@link #validation()}, the
     *         predicate it belongs to; {@link Validation#message()} is the message of the bounds
     *         that replace it. No reader carries it. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    String validationMessage() default "";

    /**
     * A type hint for converting the value.
     *
     * @return type class
     * @deprecated since 0.13.0, for removal in 1.0.0. The parameter's declared type is the
     *         conversion target and is carried as {@code ActionParamMetadata.type}; a second
     *         declaration of it could only agree or contradict. Remove the attribute. No reader
     *         carries it. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    Class<?> type() default void.class;

    /**
     * The element type of a collection parameter.
     *
     * @return element type
     * @deprecated since 0.13.0, for removal in 1.0.0. The declared type carries its type
     *         arguments, so {@code ActionParamMetadata.type} of a {@code List<String>} parameter
     *         already names the element type. Remove the attribute. No reader carries it. See
     *         {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    Class<?> elementType() default void.class;

    /**
     * Whether the value is sanitized against injection.
     *
     * @return true to sanitize
     * @deprecated since 0.13.0, for removal in 1.0.0, with no replacement. The value is bound
     *         as a typed parameter and is never spliced into query text, so there is nothing for
     *         the flag to switch. No reader carries it. See {@code MIGRATION.md}.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    boolean sanitize() default true;
}
