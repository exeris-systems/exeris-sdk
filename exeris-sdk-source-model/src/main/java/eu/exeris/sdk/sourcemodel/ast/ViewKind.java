package eu.exeris.sdk.sourcemodel.ast;

/**
 * The kind of presentation artifact a {@link ViewMetadata} describes.
 * AST-owned (mirroring {@code @View.Kind} on the annotation side without depending
 * on it), null-tolerated on the wire — {@link ViewMetadata#effectiveKind()}
 * applies the {@link #PAGE} default. Part of the reserved presentation IR surface
 * (RFC-2026-06-25).
 *
 * @since 0.8.0
 */
public enum ViewKind {
    /** A route-bearing page. */
    PAGE,
    /** A composable section of a page. */
    SECTION,
    /** A reusable component node. */
    COMPONENT,
    /** A non-route fragment (e.g. a navigation tree). */
    FRAGMENT
}
