package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the <strong>data binding</strong> of a presentation member (a field or
 * record component of a {@link View} class) — what data, if any, a
 * {@link Block} node draws from. The binding is an opaque descriptor: a
 * {@link Source} discriminator plus opaque {@code ref} / {@code path} /
 * {@code expression} strings the SDK never resolves. A node bound to nothing
 * ({@link Source#STATIC} / {@link Source#NONE}) is authored content — a complete,
 * backend-less front.
 *
 * <p><strong>Open-Core status — LIVE (structurally):</strong> extracted by the
 * {@code exeris-tooling} processor and emitted by the codegen-ts Angular view
 * generator (RFC-2026-06-28, tooling). The remaining piece is the ADR-047
 * leaf-field facet ({@code @UI}'s field-level render detail) — until it lands,
 * field-level presentation stays on {@code @UI}.
 *
 * @since 0.8
 * @see View
 * @see Block
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Bind {

    /**
     * The kind of data source this binding references.
     *
     * @return the binding source, defaulting to {@link Source#NONE}
     */
    Source source() default Source.NONE;

    /**
     * The opaque name of the referenced artifact (an entity, projection, or
     * action name). The SDK never resolves it.
     *
     * @return the artifact reference name, or empty
     */
    String ref() default "";

    /**
     * The opaque field path within the referenced artifact. The SDK never
     * resolves it against any data model.
     *
     * @return the field path, or empty
     */
    String path() default "";

    /**
     * An optional opaque expression producing the bound value (reusing the
     * declarative-behaviour expression-tag convention). The SDK never evaluates it.
     *
     * @return the expression, or empty
     */
    String expression() default "";

    /**
     * The language tag for {@link #expression()}. Empty means the SDK's SpEL
     * convention applies.
     *
     * @return the expression language tag, or empty
     */
    String language() default "";

    /**
     * The kind of data a presentation node binds to. Mirrored by the AST-owned
     * {@code eu.exeris.sdk.sourcemodel.ast.BindSource}.
     */
    enum Source {
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
}
