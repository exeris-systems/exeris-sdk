package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a member (a field or record component of a {@link View} class) as a
 * <strong>component node / block</strong> in the presentation composition tree.
 * The {@link BlockType} taxonomy is a coarse, page-composition vocabulary
 * (hero / list / grid / nav / slot / …), distinct from {@code @UI.ComponentType}
 * which is form-field granularity; {@link BlockType#CUSTOM} plus
 * {@link #customType()} is the escape hatch out of the closed enum.
 *
 * <p><strong>Open-Core status — LIVE (structurally):</strong> extracted by the
 * {@code exeris-tooling} processor and emitted by the codegen-ts Angular view
 * generator (RFC-2026-06-28, tooling). The remaining piece is the ADR-047
 * leaf-field facet ({@code @UI}'s field-level render detail) — until it lands,
 * field-level presentation stays on {@code @UI}.
 *
 * @since 0.8
 * @see View
 * @see Region
 * @see Bind
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Block {

    /**
     * The block type — the coarse page-composition kind of this node.
     *
     * @return the block type, defaulting to {@link BlockType#CONTAINER}
     */
    BlockType type() default BlockType.CONTAINER;

    /**
     * Custom block identifier, the escape hatch out of the closed
     * {@link BlockType} enum. Only meaningful when {@link #type()} is
     * {@link BlockType#CUSTOM}: it names the application-supplied block the
     * generator should render. For any built-in {@code BlockType} it is ignored.
     *
     * @return the custom block identifier, or empty when not custom
     */
    String customType() default "";

    /**
     * Additional block-specific properties as an opaque JSON object string
     * (the {@code @UI.props} convention). The SDK never parses it.
     *
     * @return the JSON props string, or empty
     */
    String props() default "";

    /**
     * The coarse page-composition block taxonomy. Mirrored by the AST-owned
     * {@code eu.exeris.sdk.sourcemodel.ast.BlockType}. Deliberately <em>not</em>
     * {@code @UI.ComponentType}, which is form-field granularity.
     */
    enum BlockType {
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
        /**
         * Application-supplied custom block (escape hatch out of this closed
         * enum). The concrete block is named by {@link Block#customType()}.
         */
        CUSTOM
    }
}
