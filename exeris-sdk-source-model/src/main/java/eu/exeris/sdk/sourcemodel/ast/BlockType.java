package eu.exeris.sdk.sourcemodel.ast;

/**
 * The coarse page-composition block taxonomy a {@link ComponentNodeMetadata}
 * carries. AST-owned (mirroring {@code @Block.BlockType} on the annotation side
 * without depending on it), null-tolerated on the wire —
 * {@link ComponentNodeMetadata#effectiveType()} applies the {@link #CONTAINER}
 * default. Deliberately distinct from {@code UIMetadata}'s form-field
 * {@code ComponentType}: this is page-composition granularity. Part of the
 * reserved presentation IR surface (RFC-2026-06-25).
 *
 * @since 0.8.0
 */
public enum BlockType {
    /** A hero / banner block. */
    HERO,
    /** A list / collection block. */
    LIST,
    /** A grid block. */
    GRID,
    /** Authored rich-text content. */
    RICH_TEXT,
    /** A navigation block. */
    NAV,
    /** A named slot into which other content is projected. */
    SLOT,
    /** A generic container grouping child nodes. */
    CONTAINER,
    /** A card block. */
    CARD,
    /** A form block. */
    FORM,
    /** An image block. */
    IMAGE,
    /** Application-supplied custom block (escape hatch out of this closed enum). */
    CUSTOM
}
