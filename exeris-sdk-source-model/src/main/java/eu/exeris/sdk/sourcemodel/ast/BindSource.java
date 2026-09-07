package eu.exeris.sdk.sourcemodel.ast;

/**
 * The kind of data source a {@link BindingMetadata} references. AST-owned
 * (mirroring {@code @Bind.Source} on the annotation side without depending on it),
 * null-tolerated on the wire — {@link BindingMetadata#effectiveSource()} applies
 * the {@link #NONE} default. Part of the reserved presentation IR surface
 * (RFC-2026-06-25).
 *
 * @since 0.8
 */
public enum BindSource {
    /** Bound to an {@code @ExerisDomain} entity, by name. */
    ENTITY,
    /** Bound to a {@code @Projection} read-model, by name. */
    PROJECTION,
    /** Bound to an {@code @Action}, by name. */
    ACTION,
    /** Authored / static literal content. */
    STATIC,
    /** Bound to a named slot the host fills. */
    SLOT,
    /** No binding — the node draws from nothing. */
    NONE
}
