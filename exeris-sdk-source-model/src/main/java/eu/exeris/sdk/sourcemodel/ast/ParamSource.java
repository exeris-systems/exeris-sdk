package eu.exeris.sdk.sourcemodel.ast;

/**
 * Where a generated request handler reads an action parameter from — carried by
 * {@link ActionParamMetadata#bindingSource()}.
 *
 * <p>AST-owned and null-tolerated on the wire: an absent value is {@link #BODY}, which
 * {@link ActionParamMetadata#effectiveBindingSource()} applies. A producer writes a value
 * only for a parameter whose source differs from that default, so metadata for an action
 * with body parameters alone is the same with or without this type.
 *
 * <p>There is no path constant. No annotation declares a path-bound action parameter, so
 * no producer could write one, and a constant nothing writes would read as a supported
 * binding.
 *
 * @since 0.13
 */
public enum ParamSource {

    /** Read from the request body, as a member of the action's request document. */
    BODY,

    /**
     * Read from the request's query string, under {@link ActionParamMetadata#effectiveQueryKey()}.
     * Declared with {@code @QueryParam}.
     */
    QUERY
}
